package dev.niceone.smartsiteappserver.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Decides which alarm raises and clears go to Centralized Fault.
 *
 * One episode per site and alarm type, shared by all nodes through
 * smartsite.alarm_notify_state: the first raise opens the episode and is sent; raises while it
 * is open are not. A clear is held until the episode has been quiet (last event a clear) for
 * quiet_minutes, then sent once by {@link #closeQuietEpisodes()}. A door or sensor that keeps
 * flapping therefore produces one raise and one clear instead of hundreds of messages.
 *
 * Every event is still stored in site_event_log; this only gates the TCP messages.
 * app.alarm.notify.mode=legacy restores the previous per-node AlarmThrottleService.
 */
@Service
@Slf4j
public class AlarmNotifyGate {

    @Value("${app.alarm.notify.mode:episode}")
    private String mode = "episode";

    @Value("${app.alarm.notify.quiet_minutes:15}")
    private int quietMinutes = 15;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AlarmThrottleService legacyThrottle;

    public static final class Episode {
        public final String siteCode;
        public final int alarmType;
        public final LocalDateTime lastEventAt;

        Episode(String siteCode, int alarmType, LocalDateTime lastEventAt) {
            this.siteCode = siteCode;
            this.alarmType = alarmType;
            this.lastEventAt = lastEventAt;
        }
    }

    boolean isLegacy() {
        return "legacy".equalsIgnoreCase(mode == null ? "" : mode.trim());
    }

    /**
     * @param eventAt when the event happened. Events can reach here out of order (other node,
     *                other thread); an older event never overwrites the state of a newer one.
     */
    public boolean shouldSend(String siteCode, int alarmType, boolean isClear, LocalDateTime eventAt) {
        if (isLegacy()) {
            return legacyThrottle.shouldSendAlarm(siteCode, alarmType, isClear);
        }
        Timestamp at = Timestamp.valueOf(eventAt != null ? eventAt : LocalDateTime.now());
        try {
            return isClear ? onClear(siteCode, alarmType, at) : onRaise(siteCode, alarmType, at);
        } catch (DataAccessException e) {
            // A duplicate message is better than a lost alarm
            log.warn("alarm gate unavailable, sending {} {}:{} - {}",
                    isClear ? "clear" : "raise", siteCode, alarmType, e.getMessage());
            return true;
        }
    }

    private boolean onRaise(String siteCode, int alarmType, Timestamp at) {
        // Already open (on any node): this raise belongs to the current episode
        int touched = jdbc.update(
                "UPDATE smartsite.alarm_notify_state SET last_state = 'RAISE', last_event_at = ? " +
                "WHERE site_code = ? AND alarm_type = ? AND is_open AND last_event_at <= ?",
                at, siteCode, alarmType, at);
        if (touched > 0) {
            log.debug("alarm raise held {}:{} - episode open", siteCode, alarmType);
            return false;
        }
        // Only the statement that flips is_open to true sends, so two nodes cannot both send.
        // Also 0 when the episode is open with a newer event: held as well.
        int opened = jdbc.update(
                "INSERT INTO smartsite.alarm_notify_state " +
                "(site_code, alarm_type, is_open, last_state, opened_at, last_event_at) " +
                "VALUES (?, ?, true, 'RAISE', ?, ?) " +
                "ON CONFLICT (site_code, alarm_type) DO UPDATE SET is_open = true, last_state = 'RAISE', " +
                "opened_at = EXCLUDED.opened_at, last_event_at = EXCLUDED.last_event_at, closed_at = NULL " +
                "WHERE NOT smartsite.alarm_notify_state.is_open",
                siteCode, alarmType, at, at);
        return opened > 0;
    }

    private boolean onClear(String siteCode, int alarmType, Timestamp at) {
        int touched = jdbc.update(
                "UPDATE smartsite.alarm_notify_state SET last_state = 'CLEAR', last_event_at = ? " +
                "WHERE site_code = ? AND alarm_type = ? AND is_open AND last_event_at <= ?",
                at, siteCode, alarmType, at);
        if (touched > 0) {
            return false;
        }
        // Open with a newer raise: this clear is stale. Not open at all (e.g. raised before this
        // version): send the clear now so the alarm does not stay stuck in Centralized Fault.
        List<Boolean> open = jdbc.queryForList(
                "SELECT is_open FROM smartsite.alarm_notify_state WHERE site_code = ? AND alarm_type = ?",
                Boolean.class, siteCode, alarmType);
        return open.isEmpty() || !open.get(0);
    }

    /**
     * Closes episodes whose last event is a clear older than quiet_minutes and returns them
     * so the caller sends one clear each. The UPDATE ... RETURNING is atomic, so an episode is
     * returned to exactly one caller even if two nodes run this at once.
     */
    public List<Episode> closeQuietEpisodes() {
        if (isLegacy()) {
            return Collections.emptyList();
        }
        return jdbc.query(
                "UPDATE smartsite.alarm_notify_state SET is_open = false, closed_at = now() " +
                "WHERE is_open AND last_state = 'CLEAR' AND last_event_at < now() - (? * interval '1 minute') " +
                "RETURNING site_code, alarm_type, last_event_at",
                (rs, i) -> new Episode(rs.getString(1), rs.getInt(2), rs.getTimestamp(3).toLocalDateTime()),
                quietMinutes);
    }
}
