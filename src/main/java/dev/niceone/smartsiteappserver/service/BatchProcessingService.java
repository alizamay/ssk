package dev.niceone.smartsiteappserver.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import dev.niceone.smartsiteappserver.utils.PerformanceLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;

@Slf4j
@Service
public class BatchProcessingService {
    
    @Autowired
    private PerformanceLogger performanceLogger;
    
    /**
     * Process items in batches with controlled parallelism
     */
    @Async("batchProcessingExecutor")
    public <T> CompletableFuture<Integer> processBatch(
            List<T> items, 
            int batchSize, 
            Consumer<T> processor,
            String operationName) {
        
        if (items == null || items.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        
        try (PerformanceLogger.PerformanceTimer timer = performanceLogger.startTimer("BatchProcess_" + operationName)) {
            
            AtomicInteger processedCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);
            
            // Process items in batches
            for (int i = 0; i < items.size(); i += batchSize) {
                int endIndex = Math.min(i + batchSize, items.size());
                List<T> batch = items.subList(i, endIndex);
                
                log.debug("Processing batch {}/{} | Size: {} | Operation: {}", 
                        (i / batchSize) + 1, (items.size() + batchSize - 1) / batchSize, 
                        batch.size(), operationName);
                
                // Process each item in the batch
                for (T item : batch) {
                    try {
                        processor.accept(item);
                        processedCount.incrementAndGet();
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                        log.error("Error processing item in batch {} | Operation: {} | Error: {}", 
                                (i / batchSize) + 1, operationName, e.getMessage());
                    }
                }
                
                // Small delay between batches to prevent overwhelming
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            int processed = processedCount.get();
            int errors = errorCount.get();
            
            log.info("Batch processing completed | Operation: {} | Processed: {} | Errors: {} | Total: {}", 
                    operationName, processed, errors, items.size());
            
            return CompletableFuture.completedFuture(processed);
            
        } catch (Exception e) {
            log.error("Batch processing failed | Operation: {} | Error: {}", operationName, e.getMessage(), e);
            return CompletableFuture.completedFuture(0);
        }
    }
    
    /**
     * Process items with parallel batches (more aggressive)
     */
    @Async("batchProcessingExecutor") 
    public <T, R> CompletableFuture<List<R>> processParallelBatches(
            List<T> items,
            int batchSize,
            int maxParallelBatches,
            Function<T, R> processor,
            String operationName) {
        
        if (items == null || items.isEmpty()) {
            return CompletableFuture.completedFuture(java.util.Collections.emptyList());
        }
        
        try (PerformanceLogger.PerformanceTimer timer = performanceLogger.startTimer("ParallelBatch_" + operationName)) {
            
            ConcurrentLinkedQueue<R> results = new ConcurrentLinkedQueue<>();
            AtomicInteger processedCount = new AtomicInteger(0);
            
            // Create parallel batch futures
            List<CompletableFuture<Void>> batchFutures = IntStream
                    .range(0, Math.min(maxParallelBatches, (items.size() + batchSize - 1) / batchSize))
                    .mapToObj(batchIndex -> CompletableFuture.runAsync(() -> {
                        int startIndex = batchIndex * batchSize;
                        int endIndex = Math.min(startIndex + batchSize, items.size());
                        
                        if (startIndex < items.size()) {
                            List<T> batch = items.subList(startIndex, endIndex);
                            
                            for (T item : batch) {
                                try {
                                    R result = processor.apply(item);
                                    if (result != null) {
                                        results.add(result);
                                    }
                                    processedCount.incrementAndGet();
                                } catch (Exception e) {
                                    log.error("Error in parallel batch {} | Operation: {} | Error: {}", 
                                            batchIndex, operationName, e.getMessage());
                                }
                            }
                        }
                    }))
                    .collect(java.util.stream.Collectors.toList());
            
            // Wait for all batches to complete
            CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0])).join();
            
            List<R> resultList = new ArrayList<>(results);
            
            log.info("Parallel batch processing completed | Operation: {} | Processed: {} | Results: {} | Batches: {}", 
                    operationName, processedCount.get(), resultList.size(), batchFutures.size());
            
            return CompletableFuture.completedFuture(resultList);
            
        } catch (Exception e) {
            log.error("Parallel batch processing failed | Operation: {} | Error: {}", operationName, e.getMessage(), e);
            return CompletableFuture.completedFuture(java.util.Collections.emptyList());
        }
    }
    
    /**
     * Smart batch processing with dynamic batch size based on processing time
     */
    @Async("batchProcessingExecutor")
    public <T> CompletableFuture<Integer> processSmartBatch(
            List<T> items, 
            Consumer<T> processor,
            String operationName) {
        
        if (items == null || items.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        
        try (PerformanceLogger.PerformanceTimer timer = performanceLogger.startTimer("SmartBatch_" + operationName)) {
            
            AtomicInteger processedCount = new AtomicInteger(0);
            int dynamicBatchSize = 10; // Start with small batch
            int maxBatchSize = 50;
            int minBatchSize = 5;
            
            // Advance by the batch just processed. The loop used to add the batch size after it
            // was adjusted below, which skipped items when it grew and repeated them when it
            // shrank: 40 of 2,936 offline sites never got a comm-lost alarm in the load test.
            for (int i = 0, endIndex; i < items.size(); i = endIndex) {
                long batchStartTime = System.currentTimeMillis();
                
                endIndex = Math.min(i + dynamicBatchSize, items.size());
                List<T> batch = items.subList(i, endIndex);
                
                // Process batch
                int batchProcessed = 0;
                for (T item : batch) {
                    try {
                        processor.accept(item);
                        batchProcessed++;
                        processedCount.incrementAndGet();
                    } catch (Exception e) {
                        log.error("Error in smart batch | Operation: {} | Error: {}", 
                                operationName, e.getMessage());
                    }
                }
                
                // Adjust batch size based on processing time
                long batchTime = System.currentTimeMillis() - batchStartTime;
                
                if (batchTime < 500) { // Fast processing - increase batch size
                    dynamicBatchSize = Math.min(dynamicBatchSize + 5, maxBatchSize);
                } else if (batchTime > 2000) { // Slow processing - decrease batch size
                    dynamicBatchSize = Math.max(dynamicBatchSize - 3, minBatchSize);
                }
                
                log.debug("Smart batch processed: {} items in {}ms | New batch size: {} | Operation: {}", 
                        batchProcessed, batchTime, dynamicBatchSize, operationName);
                
                // Adaptive delay based on processing time
                if (batchTime > 1000) {
                    try {
                        Thread.sleep(100); // Longer delay for heavy processing
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            
            int processed = processedCount.get();
            
            log.info("Smart batch processing completed | Operation: {} | Processed: {} | Total: {}", 
                    operationName, processed, items.size());
            
            return CompletableFuture.completedFuture(processed);
            
        } catch (Exception e) {
            log.error("Smart batch processing failed | Operation: {} | Error: {}", operationName, e.getMessage(), e);
            return CompletableFuture.completedFuture(0);
        }
    }
}