package dev.niceone.smartsiteappserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
public class MonitoringConfig {
    
    private ScheduledExecutorService monitoringScheduler;
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    
    @PostConstruct
    public void startMonitoring() {
        monitoringScheduler = Executors.newScheduledThreadPool(1);
        
        // Memory monitoring every 5 minutes
        monitoringScheduler.scheduleAtFixedRate(this::logMemoryUsage, 0, 5, TimeUnit.MINUTES);
        
        // Thread monitoring every 2 minutes  
        monitoringScheduler.scheduleAtFixedRate(this::logThreadUsage, 1, 2, TimeUnit.MINUTES);
        
        log.info("Monitoring started - Memory and Thread tracking enabled");
    }
    
    @PreDestroy
    public void stopMonitoring() {
        if (monitoringScheduler != null) {
            monitoringScheduler.shutdown();
            try {
                if (!monitoringScheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    monitoringScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                monitoringScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("Monitoring stopped");
    }
    
    private void logMemoryUsage() {
        long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
        long heapMax = memoryBean.getHeapMemoryUsage().getMax();
        long nonHeapUsed = memoryBean.getNonHeapMemoryUsage().getUsed();
        
        double heapUsedMB = heapUsed / (1024.0 * 1024.0);
        double heapMaxMB = heapMax / (1024.0 * 1024.0);
        double nonHeapUsedMB = nonHeapUsed / (1024.0 * 1024.0);
        double heapPercent = (heapUsed * 100.0) / heapMax;
        
        // SLF4J has no "{:.1f}" placeholder (it was logged literally), so format explicitly.
        log.info(String.format("MEMORY_MONITOR | Heap: %.1fMB/%.1fMB (%.1f%%) | NonHeap: %.1fMB",
                heapUsedMB, heapMaxMB, heapPercent, nonHeapUsedMB));

        // Warning for high memory usage
        if (heapPercent > 80) {
            log.warn(String.format("HIGH_MEMORY_USAGE | Heap usage: %.1f%% - Consider memory optimization", heapPercent));
        }
    }
    
    private void logThreadUsage() {
        int threadCount = threadBean.getThreadCount();
        int peakThreadCount = threadBean.getPeakThreadCount();
        long totalStartedThreadCount = threadBean.getTotalStartedThreadCount();
        
        log.info("THREAD_MONITOR | Active: {} | Peak: {} | Total Started: {}", 
                threadCount, peakThreadCount, totalStartedThreadCount);
        
        // Warning for high thread count
        if (threadCount > 200) {
            log.warn("HIGH_THREAD_COUNT | Active threads: {} - Possible thread leak", threadCount);
        }
        
        // Log thread details if too many threads
        if (threadCount > 150) {
            long[] threadIds = threadBean.getAllThreadIds();
            log.info("THREAD_DETAIL | Thread IDs count: {}", threadIds.length);
        }
    }
    
    @Bean("monitoredTaskExecutor") 
    public ThreadPoolTaskExecutor monitoredTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("Monitor-");
        executor.setRejectedExecutionHandler((r, e) -> {
            log.error("TASK_REJECTED | Task: {} | Pool: active={}/max={}", 
                    r.getClass().getSimpleName(), e.getActiveCount(), e.getMaximumPoolSize());
        });
        executor.initialize();
        
        return executor;
    }
}