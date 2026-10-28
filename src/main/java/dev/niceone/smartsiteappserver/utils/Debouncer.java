package dev.niceone.smartsiteappserver.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Collapses reports per key (site) and processes the newest one after a short delay.
 *
 * The single scheduler thread only keeps time; callbacks run on a pool of workers so one slow
 * site does not hold up every other site. A key is never processed by two workers at once:
 * if the previous report of a site is still running, the pending task stays in the map,
 * keeps taking newer payloads and retries shortly. Memory therefore stays at one running plus
 * one pending report per key, however far processing falls behind.
 */
@Slf4j
public class Debouncer <T> {
    private static final long BUSY_RETRY_MS = 200;

    private final ScheduledExecutorService sched = Executors.newScheduledThreadPool(1, named("debounce-timer"));
    private final ExecutorService workers;
    private final ConcurrentHashMap<String, TimerTask> delayedMap = new ConcurrentHashMap<String, TimerTask>();
    private final Set<String> running = ConcurrentHashMap.newKeySet();
    private final Callback<T> callback;
    private final int interval;

    public Debouncer(Callback<T> c, int interval) {
        this(c, interval, 1);
    }

    public Debouncer(Callback<T> c, int interval, int workerCount) {
        this.callback = c;
        this.interval = interval;
        int n = Math.max(1, workerCount);
        // Unbounded queue is safe: a key is queued at most once (see running), so it holds at most one entry per site
        this.workers = new ThreadPoolExecutor(n, n, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), named("report-worker"));
    }

    private static ThreadFactory named(String prefix) {
        AtomicInteger seq = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, prefix + "-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
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
        } while (prev != null && !prev.extend(extra)); // Exit only if new task was added to map, or existing task was extended successfully
    }

    /** Keys waiting for their delay or for a worker; for monitoring. */
    public int pendingCount() {
        return delayedMap.size();
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
        workers.shutdownNow();
        try {
            if (!sched.awaitTermination(5, TimeUnit.SECONDS) || !workers.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Debouncer tasks did not terminate gracefully");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // The task that wakes up when the wait time elapses
    private class TimerTask implements Runnable {
        private final String key;
        private T extra;
        private long dueTime;
        private final Object lock = new Object();

        public TimerTask(String key, T extra) {
            this.key = key;
            this.extra = extra;
            extend(null);
        }

        // Keep the newest payload so a later report (e.g. door closed) is not lost to an earlier one
        public boolean extend(T newExtra) {
            synchronized (lock) {
                if (dueTime < 0) // Task has been shutdown
                    return false;
                dueTime = System.currentTimeMillis() + 0; //no need extend time
                if (newExtra != null) this.extra = newExtra;
                return true;
            }
        }

        public void run() {
            T payload;
            synchronized (lock) {
                if (dueTime < 0) { // cancelled by terminate()
                    return;
                }
                long remaining = dueTime - System.currentTimeMillis();
                if (remaining > 0) { // Re-schedule task
                    sched.schedule(this, remaining, TimeUnit.MILLISECONDS);
                    return;
                }
                if (!running.add(key)) { // previous report of this key still processing
                    sched.schedule(this, BUSY_RETRY_MS, TimeUnit.MILLISECONDS);
                    return;
                }
                // From here extend() fails, so callers create a new task for the next report
                dueTime = -1;
                delayedMap.remove(key, this);
                payload = extra;
            }
            try {
                workers.execute(() -> {
                    try {
                        callback.call(key, payload);
                    } catch (Exception e) {
                        log.warn("report processing failed for {}: {}", key, e.toString());
                    } finally {
                        running.remove(key);
                    }
                });
            } catch (RejectedExecutionException e) { // shutting down
                running.remove(key);
            }
        }
    }
}
