package dev.niceone.smartsiteappserver.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.Random;
import java.util.concurrent.*;

@Slf4j
public class Debouncer <T> {
    private final ScheduledExecutorService sched = Executors.newScheduledThreadPool(1);
    private final ConcurrentHashMap<String, TimerTask> delayedMap = new ConcurrentHashMap<String, TimerTask>();
    private final Callback<T> callback;
    private final int interval;

    public Debouncer(Callback<T> c, int interval) {
        this.callback = c;
        this.interval = interval;
    }

    public void call(String key, T extra) {
        TimerTask task = new TimerTask(key, extra);

        TimerTask prev = null;
        do {
            prev = delayedMap.putIfAbsent(key, task);
            if (prev == null) {
                int randomInterval = ThreadLocalRandom.current().nextInt(10, 2000);
                sched.schedule(task, interval+randomInterval, TimeUnit.MILLISECONDS);
            }
        } while (prev != null && !prev.extend()); // Exit only if new task was added to map, or existing task was extended successfully
    }

    public void terminate() {
        // Cancel all pending tasks before shutting down
        delayedMap.values().forEach(task -> {
            synchronized (task.lock) {
                task.dueTime = -1; // Mark as cancelled
            }
        });
        delayedMap.clear();
        
        sched.shutdownNow();
        try {
            if (!sched.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Debouncer tasks did not terminate gracefully");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // The task that wakes up when the wait time elapses
    private class TimerTask implements Runnable {
        private final String key;
        private final T extra;
        private long dueTime;
        private final Object lock = new Object();

        public TimerTask(String key, T extra) {
            this.key = key;
            this.extra = extra;
            extend();
        }

        public boolean extend() {
            synchronized (lock) {
                if (dueTime < 0) // Task has been shutdown
                    return false;
                dueTime = System.currentTimeMillis() + 0; //no need extend time
                return true;
            }
        }

        public void run() {
            synchronized (lock) {
                long remaining = dueTime - System.currentTimeMillis();
                if (remaining > 0) { // Re-schedule task
                    sched.schedule(this, remaining, TimeUnit.MILLISECONDS);
                } else { // Mark as terminated and invoke callback
                    dueTime = -1;
                    try {
                        callback.call(key, extra);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    } finally {
                        delayedMap.remove(key);
                    }
                }
            }
        }
    }
}