package dev.niceone.smartsiteappserver.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;

@Slf4j
@Component
public class PerformanceLogger {
    
    private final Map<String, AtomicLong> operationCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> operationTimes = new ConcurrentHashMap<>();
    
    public static class PerformanceTimer implements AutoCloseable {
        private final StopWatch stopWatch;
        private final String operation;
        private final PerformanceLogger logger;
        
        public PerformanceTimer(String operation, PerformanceLogger logger) {
            this.operation = operation;
            this.logger = logger;
            this.stopWatch = new StopWatch(operation);
            this.stopWatch.start();
        }
        
        @Override
        public void close() {
            if (stopWatch.isRunning()) {
                stopWatch.stop();
                long executionTime = stopWatch.getTotalTimeMillis();
                logger.recordExecution(operation, executionTime);
                
                if (executionTime > 1000) { // Log slow operations (>1s)
                    log.warn("SLOW_OPERATION | {} took {}ms", operation, executionTime);
                } else if (executionTime > 5000) { // Very slow operations (>5s)
                    log.error("VERY_SLOW_OPERATION | {} took {}ms", operation, executionTime);
                }
            }
        }
    }
    
    public PerformanceTimer startTimer(String operation) {
        return new PerformanceTimer(operation, this);
    }
    
    private void recordExecution(String operation, long executionTime) {
        operationCounts.computeIfAbsent(operation, k -> new AtomicLong(0)).incrementAndGet();
        operationTimes.computeIfAbsent(operation, k -> new AtomicLong(0)).addAndGet(executionTime);
        
        // Log every 100th execution for statistics
        long count = operationCounts.get(operation).get();
        if (count % 100 == 0) {
            long totalTime = operationTimes.get(operation).get();
            long avgTime = totalTime / count;
            log.info("PERF_STATS | {} | Count: {} | Avg: {}ms | Total: {}ms", 
                    operation, count, avgTime, totalTime);
        }
    }
    
    public void logCurrentStats() {
        log.info("=== Performance Statistics ===");
        operationCounts.forEach((operation, count) -> {
            long totalTime = operationTimes.get(operation).get();
            long avgTime = totalTime / count.get();
            log.info("PERF_SUMMARY | {} | Count: {} | Avg: {}ms | Total: {}ms", 
                    operation, count.get(), avgTime, totalTime);
        });
        log.info("=== End Performance Statistics ===");
    }
    
    // Helper method for database operations
    public void logDatabaseQuery(String query, long executionTime) {
        if (executionTime > 500) {
            log.warn("SLOW_QUERY | {}ms | {}", executionTime, 
                    query.length() > 100 ? query.substring(0, 100) + "..." : query);
        }
    }
    
    // Helper method for API calls
    public void logApiCall(String endpoint, int responseCode, long executionTime) {
        if (executionTime > 2000 || responseCode >= 400) {
            log.warn("API_ISSUE | {} | Status: {} | Time: {}ms", endpoint, responseCode, executionTime);
        } else {
            log.debug("API_CALL | {} | Status: {} | Time: {}ms", endpoint, responseCode, executionTime);
        }
    }
    
    // Helper method for scheduler operations
    public void logSchedulerExecution(String schedulerName, int processedItems, long executionTime) {
        log.info("SCHEDULER_EXEC | {} | Items: {} | Time: {}ms | Rate: {:.1f} items/sec", 
                schedulerName, processedItems, executionTime, 
                processedItems / (executionTime / 1000.0));
        
        if (executionTime > 30000) { // 30 seconds
            log.warn("SCHEDULER_SLOW | {} took {}ms to process {} items", 
                    schedulerName, executionTime, processedItems);
        }
    }
}