package dev.niceone.smartsiteappserver.service;

import dev.niceone.smartsiteappserver.model.*;
import dev.niceone.smartsiteappserver.model.constant.StatusCodeEnum;
import dev.niceone.smartsiteappserver.payload.request.StatusReportRequest;
import dev.niceone.smartsiteappserver.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@Slf4j
@Service
public class HighThroughputStatusService {
    
    // In-memory cache for ultra-fast lookups
    private final ConcurrentHashMap<String, SiteStatusCache> siteCache = new ConcurrentHashMap<>();
    private final LinkedBlockingQueue<StatusReportRequest> incomingQueue = new LinkedBlockingQueue<>();
    private final ReentrantReadWriteLock cacheLock = new ReentrantReadWriteLock();
    
    // Performance counters
    private final AtomicLong requestsPerSecond = new AtomicLong(0);
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong droppedRequests = new AtomicLong(0);
    
    @Autowired
    private SiteRepository siteRepository;
    
    @Autowired
    private CabinetRepository cabinetRepository;
    
    @Autowired
    private BatchProcessingService batchProcessingService;
    
    private volatile boolean running = true;
    private Thread[] processingThreads;
    private Thread batchWriterThread;
    
    // Cache structure for fast lookups
    private static class SiteStatusCache {
        String siteCode;
        Long siteId;
        LocalDateTime lastUpdate;
        StatusReportRequest lastReport;
        boolean isDirty; // Needs database sync
        
        SiteStatusCache(String siteCode, Long siteId) {
            this.siteCode = siteCode;
            this.siteId = siteId;
            this.lastUpdate = LocalDateTime.now();
            this.isDirty = false;
        }
    }
    
    @PostConstruct
    public void initialize() {
        log.info("Initializing High-Throughput Status Service...");
        
        // Pre-load site cache for ultra-fast lookups
        preloadSiteCache();
        
        // Start processing threads (8 threads for parallel processing)
        startProcessingThreads(8);
        
        // Start batch writer thread
        startBatchWriter();
        
        log.info("High-Throughput Status Service initialized with {} sites in cache", siteCache.size());
    }
    
    private void preloadSiteCache() {
        cacheLock.writeLock().lock();
        try {
            List<Site> allSites = siteRepository.findByActiveAndIsInstall(true, true);
            for (Site site : allSites) {
                siteCache.put(site.getCode(), new SiteStatusCache(site.getCode(), site.getId()));
            }
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
    
    private void startProcessingThreads(int threadCount) {
        processingThreads = new Thread[threadCount];
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            processingThreads[i] = new Thread(() -> {
                processIncomingRequests(threadId);
            }, "StatusProcessor-" + threadId);
            processingThreads[i].start();
        }
    }
    
    private void startBatchWriter() {
        batchWriterThread = new Thread(() -> {
            batchWriteToDatabase();
        }, "StatusBatchWriter");
        batchWriterThread.start();
    }
    
    /**
     * Ultra-fast status report processing
     */
    public boolean processStatusReport(StatusReportRequest request) {
        totalRequests.incrementAndGet();
        
        // Quick validation
        if (request == null || request.getSite_code() == null || request.getSite_code().trim().isEmpty()) {
            droppedRequests.incrementAndGet();
            return false;
        }
        
        // Non-blocking queue add with capacity limit
        boolean queued = incomingQueue.offer(request);
        if (!queued) {
            droppedRequests.incrementAndGet();
            log.warn("Queue full! Dropped request from site: {}", request.getSite_code());
            return false;
        }
        
        return true;
    }
    
    private void processIncomingRequests(int threadId) {
        log.info("Status processor thread {} started", threadId);
        
        while (running) {
            try {
                // Process requests from queue
                StatusReportRequest request = incomingQueue.take(); // Blocking
                processRequest(request);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error in status processor thread {}: {}", threadId, e.getMessage());
            }
        }
        
        log.info("Status processor thread {} stopped", threadId);
    }
    
    private void processRequest(StatusReportRequest request) {
        String siteCode = request.getSite_code();
        
        cacheLock.readLock().lock();
        try {
            SiteStatusCache cache = siteCache.get(siteCode);
            
            if (cache == null) {
                // Site not found in cache - might be new site
                handleUnknownSite(siteCode, request);
                return;
            }
            
            // Update cache (in-memory, ultra-fast)
            cache.lastReport = request;
            cache.lastUpdate = LocalDateTime.now();
            cache.isDirty = true; // Mark for database sync
            
            // Increment request counter
            requestsPerSecond.incrementAndGet();
            
        } finally {
            cacheLock.readLock().unlock();
        }
    }
    
    private void handleUnknownSite(String siteCode, StatusReportRequest request) {
        // Non-blocking lookup for new site
        Site site = siteRepository.findByCode(siteCode).orElse(null);
        if (site != null) {
            cacheLock.writeLock().lock();
            try {
                SiteStatusCache newCache = new SiteStatusCache(siteCode, site.getId());
                newCache.lastReport = request;
                newCache.lastUpdate = LocalDateTime.now();
                newCache.isDirty = true;
                siteCache.put(siteCode, newCache);
                log.info("Added new site to cache: {}", siteCode);
            } finally {
                cacheLock.writeLock().unlock();
            }
        } else {
            droppedRequests.incrementAndGet();
            log.warn("Unknown or inactive site: {}", siteCode);
        }
    }
    
    /**
     * Batch write dirty records to database every 1-2 seconds
     */
    private void batchWriteToDatabase() {
        log.info("Batch writer thread started");
        
        while (running) {
            try {
                Thread.sleep(1500); // Batch every 1.5 seconds
                
                List<SiteStatusCache> dirtyRecords = new ArrayList<>();
                
                cacheLock.readLock().lock();
                try {
                    // Collect dirty records
                    for (SiteStatusCache cache : siteCache.values()) {
                        if (cache.isDirty && cache.lastReport != null) {
                            dirtyRecords.add(cache);
                        }
                    }
                } finally {
                    cacheLock.readLock().unlock();
                }
                
                if (!dirtyRecords.isEmpty()) {
                    batchUpdateDatabase(dirtyRecords);
                    
                    // Mark records as clean
                    for (SiteStatusCache cache : dirtyRecords) {
                        cache.isDirty = false;
                    }
                    
                    log.debug("Batch updated {} sites to database", dirtyRecords.size());
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error in batch writer: {}", e.getMessage(), e);
            }
        }
        
        log.info("Batch writer thread stopped");
    }
    
    private void batchUpdateDatabase(List<SiteStatusCache> dirtyRecords) {
        // Use batch processing service for database writes
        batchProcessingService.processBatch(dirtyRecords, 50, cache -> {
            try {
                // Update site status
                Site site = siteRepository.findById(cache.siteId).orElse(null);
                if (site != null) {
                    site.setStatus(StatusCodeEnum.STATUS_UP);
                    site.setReportStatusAt(cache.lastUpdate);
                    site.setUpdatedAt(LocalDateTime.now());
                    siteRepository.save(site);
                    
                    // Update cabinets if needed
                    updateCabinetStatus(cache.lastReport);
                }
            } catch (Exception e) {
                log.error("Error updating site {}: {}", cache.siteCode, e.getMessage());
            }
        }, "DatabaseSync");
    }
    
    private void updateCabinetStatus(StatusReportRequest request) {
        // Process cabinet updates (implement based on existing logic)
        // This should be optimized for batch processing
    }
    
    /**
     * Performance monitoring - log stats every 10 seconds
     */
    @Scheduled(fixedDelay = 10000)
    public void logPerformanceStats() {
        long rps = requestsPerSecond.getAndSet(0);
        long total = totalRequests.get();
        long dropped = droppedRequests.get();
        int queueSize = incomingQueue.size();
        int cacheSize = siteCache.size();
        
        log.info("STATUS_PERF | RPS: {} | Total: {} | Dropped: {} | Queue: {} | Cache: {}", 
                rps / 10, total, dropped, queueSize, cacheSize);
        
        // Alert if performance degraded
        if (rps < 100 && total > 1000) {
            log.warn("LOW_THROUGHPUT | RPS dropped to {}/sec", rps / 10);
        }
        
        if (queueSize > 1000) {
            log.warn("QUEUE_BACKLOG | {} requests pending", queueSize);
        }
    }
    
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down High-Throughput Status Service...");
        running = false;
        
        // Stop processing threads
        for (Thread thread : processingThreads) {
            thread.interrupt();
        }
        
        // Stop batch writer
        batchWriterThread.interrupt();
        
        // Wait for threads to finish
        try {
            for (Thread thread : processingThreads) {
                thread.join(5000);
            }
            batchWriterThread.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Flush remaining data
        flushRemainingData();
        
        log.info("High-Throughput Status Service shutdown completed");
    }
    
    private void flushRemainingData() {
        // Process remaining queue items
        while (!incomingQueue.isEmpty()) {
            StatusReportRequest request = incomingQueue.poll();
            if (request != null) {
                processRequest(request);
            }
        }
        
        // Final database sync
        List<SiteStatusCache> dirtyRecords = new ArrayList<>();
        for (SiteStatusCache cache : siteCache.values()) {
            if (cache.isDirty && cache.lastReport != null) {
                dirtyRecords.add(cache);
            }
        }
        
        if (!dirtyRecords.isEmpty()) {
            batchUpdateDatabase(dirtyRecords);
            log.info("Flushed {} remaining updates to database", dirtyRecords.size());
        }
    }
}