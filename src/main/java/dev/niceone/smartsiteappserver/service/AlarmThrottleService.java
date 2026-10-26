package dev.niceone.smartsiteappserver.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Alarm Throttle Service - Prevents alarm flooding to Autin system
 *
 * Problem: Faulty equipment (broken door/sensor) causes rapid alarm create/clear cycles
 * Example: Door swings → UNAUTH_OPENED create → clear → create → clear (many times)
 * Result: Floods Autin system with duplicate alarms
 *
 * Solution: Deduplication with time-based throttling
 * - Detect flapping (rapid create/clear within time window)
 * - Allow first alarm, suppress duplicates during flapping period
 * - Send summary after stabilization
 *
 * @author Claude Code
 * @since 2025-12-18
 */
@Service
@Slf4j
public class AlarmThrottleService {

    // Configuration constants
    private static final int THROTTLE_WINDOW_SECONDS = 300;  // 5 minutes - don't send duplicates within this window
    private static final int FLAPPING_THRESHOLD = 3;         // If 3+ events in window, consider it flapping
    private static final int FLAPPING_WINDOW_SECONDS = 60;   // Check for flapping within 1 minute
    private static final int STABILIZATION_WINDOW_SECONDS = 180; // Wait 3 minutes after last event before sending summary

    /**
     * Alarm event tracker for detecting flapping and throttling
     */
    private static class AlarmTracker {
        String siteCode;
        Integer alarmType;
        LocalDateTime firstEventTime;
        LocalDateTime lastEventTime;
        LocalDateTime lastSentTime;
        int eventCount;              // Total events in current window
        int createCount;             // Number of CREATE events
        int clearCount;              // Number of CLEAR events
        boolean isFlapping;          // Is currently in flapping state
        boolean summarySent;         // Has summary been sent after stabilization

        public AlarmTracker(String siteCode, Integer alarmType) {
            this.siteCode = siteCode;
            this.alarmType = alarmType;
            this.firstEventTime = LocalDateTime.now();
            this.lastEventTime = LocalDateTime.now();
            this.lastSentTime = null;
            this.eventCount = 1;
            this.createCount = 0;
            this.clearCount = 0;
            this.isFlapping = false;
            this.summarySent = false;
        }
    }

    // Key format: "siteCode:alarmType"
    private final Map<String, AlarmTracker> alarmTrackers = new ConcurrentHashMap<>();

    /**
     * Check if alarm should be sent to Autin system
     *
     * @param siteCode Site code
     * @param alarmType Alarm type ID
     * @param isClear true if this is a CLEAR event, false if CREATE
     * @return true if should send to Autin, false if should suppress (throttled)
     */
    public boolean shouldSendAlarm(String siteCode, Integer alarmType, boolean isClear) {
        String key = buildKey(siteCode, alarmType);
        LocalDateTime now = LocalDateTime.now();

        AlarmTracker tracker = alarmTrackers.computeIfAbsent(key, k -> new AlarmTracker(siteCode, alarmType));

        // Update event counts
        tracker.lastEventTime = now;
        tracker.eventCount++;
        if (isClear) {
            tracker.clearCount++;
        } else {
            tracker.createCount++;
        }

        // Check if we're in flapping state
        checkFlappingState(tracker, now);

        // If not flapping and no recent send, allow it
        if (!tracker.isFlapping) {
            if (tracker.lastSentTime == null ||
                tracker.lastSentTime.plusSeconds(THROTTLE_WINDOW_SECONDS).isBefore(now)) {

                tracker.lastSentTime = now;
                log.info("✓ Alarm allowed [{}:{}] - Not flapping, within throttle window",
                    siteCode, alarmType);
                return true;
            } else {
                log.debug("⊗ Alarm throttled [{}:{}] - Within throttle window ({}s remaining)",
                    siteCode, alarmType,
                    THROTTLE_WINDOW_SECONDS - java.time.Duration.between(tracker.lastSentTime, now).getSeconds());
                return false;
            }
        }

        // We're in flapping state - suppress all alarms
        log.warn("⚠ Alarm SUPPRESSED [{}:{}] - Flapping detected! Events: {} ({}C/{}R) in {}s",
            siteCode, alarmType, tracker.eventCount, tracker.createCount, tracker.clearCount,
            java.time.Duration.between(tracker.firstEventTime, now).getSeconds());

        return false;
    }

    /**
     * Check if alarm pattern indicates flapping
     *
     * Flapping criteria:
     * - 3+ events within 1 minute
     * - Mix of CREATE and CLEAR events (oscillating)
     */
    private void checkFlappingState(AlarmTracker tracker, LocalDateTime now) {
        long secondsSinceFirst = java.time.Duration.between(tracker.firstEventTime, now).getSeconds();

        // Check if we're within flapping detection window
        if (secondsSinceFirst <= FLAPPING_WINDOW_SECONDS) {
            // If we have threshold number of events, it's flapping
            if (tracker.eventCount >= FLAPPING_THRESHOLD) {
                // Must have both CREATE and CLEAR to be considered flapping (oscillating)
                if (tracker.createCount > 0 && tracker.clearCount > 0) {
                    if (!tracker.isFlapping) {
                        tracker.isFlapping = true;
                        log.error("🚨 FLAPPING DETECTED [{}:{}] - {} events ({}C/{}R) in {}s - Suppressing alarms",
                            tracker.siteCode, tracker.alarmType, tracker.eventCount,
                            tracker.createCount, tracker.clearCount, secondsSinceFirst);
                    }
                }
            }
        } else {
            // Outside flapping window - check if we need to reset
            long secondsSinceLast = java.time.Duration.between(tracker.lastEventTime, now).getSeconds();

            if (secondsSinceLast > STABILIZATION_WINDOW_SECONDS) {
                // Stabilized - send summary if needed
                if (tracker.isFlapping && !tracker.summarySent) {
                    log.info("📊 Alarm stabilized [{}:{}] - Flapping ended. Summary: {} total events ({}C/{}R) over {}s",
                        tracker.siteCode, tracker.alarmType, tracker.eventCount,
                        tracker.createCount, tracker.clearCount,
                        java.time.Duration.between(tracker.firstEventTime, tracker.lastEventTime).getSeconds());
                    tracker.summarySent = true;
                    // Could send a summary alarm here if needed
                }

                // Reset tracker for new window
                alarmTrackers.remove(buildKey(tracker.siteCode, tracker.alarmType));
                log.debug("♻ Alarm tracker reset [{}:{}] - Stabilization period complete",
                    tracker.siteCode, tracker.alarmType);
            }
        }
    }

    /**
     * Get statistics for a specific site and alarm type
     *
     * @param siteCode Site code
     * @param alarmType Alarm type ID
     * @return Statistics string, or null if no tracker exists
     */
    public String getAlarmStats(String siteCode, Integer alarmType) {
        String key = buildKey(siteCode, alarmType);
        AlarmTracker tracker = alarmTrackers.get(key);

        if (tracker == null) {
            return null;
        }

        long durationSeconds = java.time.Duration.between(tracker.firstEventTime, tracker.lastEventTime).getSeconds();
        return String.format("Site: %s, AlarmType: %d, Events: %d (C:%d/R:%d), Duration: %ds, Flapping: %s",
            siteCode, alarmType, tracker.eventCount, tracker.createCount, tracker.clearCount,
            durationSeconds, tracker.isFlapping);
    }

    /**
     * Get all current alarm statistics (for monitoring/debugging)
     *
     * @return Map of alarm stats
     */
    public Map<String, String> getAllAlarmStats() {
        Map<String, String> stats = new ConcurrentHashMap<>();
        alarmTrackers.forEach((key, tracker) -> {
            stats.put(key, getAlarmStats(tracker.siteCode, tracker.alarmType));
        });
        return stats;
    }

    /**
     * Manually reset alarm tracker (for testing or manual intervention)
     *
     * @param siteCode Site code
     * @param alarmType Alarm type ID
     */
    public void resetAlarmTracker(String siteCode, Integer alarmType) {
        String key = buildKey(siteCode, alarmType);
        AlarmTracker tracker = alarmTrackers.remove(key);
        if (tracker != null) {
            log.info("🔄 Manually reset alarm tracker [{}:{}]", siteCode, alarmType);
        }
    }

    /**
     * Clear all alarm trackers (for maintenance)
     */
    public void clearAllTrackers() {
        int count = alarmTrackers.size();
        alarmTrackers.clear();
        log.info("🧹 Cleared all alarm trackers ({} trackers removed)", count);
    }

    /**
     * Build unique key for alarm tracker
     */
    private String buildKey(String siteCode, Integer alarmType) {
        return siteCode + ":" + alarmType;
    }

    /**
     * Scheduled cleanup of old trackers (run periodically)
     * This prevents memory leaks from sites that never stabilize
     */
    public void cleanupOldTrackers() {
        LocalDateTime now = LocalDateTime.now();
        int removedCount = 0;

        for (Map.Entry<String, AlarmTracker> entry : alarmTrackers.entrySet()) {
            AlarmTracker tracker = entry.getValue();
            long secondsSinceLast = java.time.Duration.between(tracker.lastEventTime, now).getSeconds();

            // Remove trackers older than 1 hour with no recent activity
            if (secondsSinceLast > 3600) {
                alarmTrackers.remove(entry.getKey());
                removedCount++;
                log.debug("🗑 Removed stale alarm tracker [{}:{}] - No activity for {}s",
                    tracker.siteCode, tracker.alarmType, secondsSinceLast);
            }
        }

        if (removedCount > 0) {
            log.info("🧹 Cleanup completed - Removed {} stale alarm trackers", removedCount);
        }
    }
}
