package dev.niceone.smartsiteappserver.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.annotation.PreDestroy;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {
    
    @Value("${app.thread.prove-offline.core-size:6}")
    private int proveOfflineCoreSize;
    
    @Value("${app.thread.prove-offline.max-size:12}")
    private int proveOfflineMaxSize;
    
    @Value("${app.thread.prove-offline.queue-capacity:50}")
    private int proveOfflineQueueCapacity;
    
    @Value("${app.thread.batch-processing.core-size:4}")
    private int batchProcessingCoreSize;
    
    @Value("${app.thread.batch-processing.max-size:8}")
    private int batchProcessingMaxSize;
    
    @Value("${app.thread.batch-processing.queue-capacity:100}")
    private int batchProcessingQueueCapacity;
    
    @Value("${app.thread.ping.core-size:50}")
    private int pingCoreSize;

    @Value("${app.thread.ping.max-size:150}")
    private int pingMaxSize;

    @Value("${app.thread.ping.queue-capacity:500}")
    private int pingQueueCapacity;

    private ThreadPoolTaskExecutor proveOfflineExecutor;
    private ThreadPoolTaskExecutor batchProcessingExecutor;
    private ThreadPoolTaskExecutor pingExecutor;
    private ThreadPoolTaskExecutor defaultAsyncExecutor;

    /**
     * Default executor สำหรับ @Async ทุกตัวที่ "ไม่ได้ระบุ" executor.
     * เดิม Spring หา default ไม่เจอ (มีหลาย Executor bean, ไม่มี @Primary) จึง fallback ไปใช้
     * SimpleAsyncTaskExecutor = สร้าง thread ใหม่ทุก task ไม่จำกัด → thread สะสมจน OOM
     * "unable to create new native thread". เปลี่ยนมาใช้ bounded pool + CallerRuns
     * เพื่อไม่ให้ thread ระเบิด และไม่ทิ้ง task เงียบๆ
     */
    @Bean(name = "defaultAsyncExecutor")
    public Executor defaultAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(20);
        executor.setMaxPoolSize(60);
        executor.setQueueCapacity(2000);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("async-default-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        this.defaultAsyncExecutor = executor;

        log.info("Default async executor initialized: core=20, max=60, queue=2000 (replaces unbounded SimpleAsyncTaskExecutor)");

        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return defaultAsyncExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) ->
                log.error("Uncaught async exception in {}(): {}", method.getName(), throwable.getMessage(), throwable);
    }


    @Bean(name = "proveOfflineTaskExecutor")
    public Executor proveOfflineTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(proveOfflineCoreSize);
        executor.setMaxPoolSize(proveOfflineMaxSize);
        executor.setQueueCapacity(proveOfflineQueueCapacity);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("ProveOffline-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        
        // Custom rejection handler to prevent task loss
        executor.setRejectedExecutionHandler((r, e) -> {
            log.warn("ProveOffline task rejected: {} | Pool: active={}/{}, queue={}/{}", 
                    r.getClass().getSimpleName(), e.getActiveCount(), e.getMaximumPoolSize(),
                    e.getQueue().size(), proveOfflineQueueCapacity);
            
            // Try to run in caller thread as fallback
            if (!e.isShutdown()) {
                try {
                    r.run();
                } catch (Exception ex) {
                    log.error("Failed to execute rejected task in caller thread", ex);
                }
            }
        });
        
        executor.initialize();
        this.proveOfflineExecutor = executor;
        
        log.info("ProveOffline ThreadPool initialized: core={}, max={}, queue={}", 
                proveOfflineCoreSize, proveOfflineMaxSize, proveOfflineQueueCapacity);
        
        return executor;
    }
    
    @Bean(name = "batchProcessingExecutor")
    public Executor batchProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(batchProcessingCoreSize);
        executor.setMaxPoolSize(batchProcessingMaxSize);
        executor.setQueueCapacity(batchProcessingQueueCapacity);
        executor.setKeepAliveSeconds(120);
        executor.setThreadNamePrefix("BatchProc-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        
        // Custom rejection handler for batch processing
        executor.setRejectedExecutionHandler((r, e) -> {
            log.error("BatchProcessing task rejected and DROPPED: {} | Pool: active={}/{}, queue={}/{}", 
                    r.getClass().getSimpleName(), e.getActiveCount(), e.getMaximumPoolSize(),
                    e.getQueue().size(), batchProcessingQueueCapacity);
            // Don't run in caller thread for batch tasks to prevent blocking
        });
        
        executor.initialize();
        this.batchProcessingExecutor = executor;
        
        log.info("BatchProcessing ThreadPool initialized: core={}, max={}, queue={}", 
                batchProcessingCoreSize, batchProcessingMaxSize, batchProcessingQueueCapacity);
        
        return executor;
    }
    
    @Bean(name = "pingExecutor")
    public Executor pingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(pingCoreSize);
        executor.setMaxPoolSize(pingMaxSize);
        executor.setQueueCapacity(pingQueueCapacity);
        executor.setKeepAliveSeconds(30);
        executor.setThreadNamePrefix("Ping-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        this.pingExecutor = executor;

        log.info("Ping ThreadPool initialized: core={}, max={}, queue={}",
                pingCoreSize, pingMaxSize, pingQueueCapacity);

        return executor;
    }

    @PreDestroy
    public void shutdownExecutors() {
        log.info("Shutting down custom thread pools...");

        if (proveOfflineExecutor != null) {
            proveOfflineExecutor.shutdown();
        }

        if (batchProcessingExecutor != null) {
            batchProcessingExecutor.shutdown();
        }

        if (pingExecutor != null) {
            pingExecutor.shutdown();
        }

        if (defaultAsyncExecutor != null) {
            defaultAsyncExecutor.shutdown();
        }

        log.info("Custom thread pools shutdown completed");
    }
}
