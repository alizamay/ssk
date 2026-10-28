package dev.niceone.smartsiteappserver.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlarmNotifyGateTest {

    private static final String RAISE_OPEN = "UPDATE smartsite.alarm_notify_state SET last_state = 'RAISE'";
    private static final String CLEAR_OPEN = "UPDATE smartsite.alarm_notify_state SET last_state = 'CLEAR'";
    private static final String OPEN_EPISODE = "INSERT INTO smartsite.alarm_notify_state";
    private static final String IS_OPEN = "SELECT is_open FROM smartsite.alarm_notify_state";
    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 28, 9, 0);

    private JdbcTemplate jdbc;
    private AlarmThrottleService legacy;
    private AlarmNotifyGate gate;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        legacy = mock(AlarmThrottleService.class);
        gate = new AlarmNotifyGate();
        ReflectionTestUtils.setField(gate, "jdbc", jdbc);
        ReflectionTestUtils.setField(gate, "legacyThrottle", legacy);
    }

    private void touchOpen(String sql, int rows) {
        when(jdbc.update(startsWith(sql), any(Timestamp.class), eq("NSRKK"), eq(9), any(Timestamp.class))).thenReturn(rows);
    }

    private void openEpisode(int rows) {
        when(jdbc.update(startsWith(OPEN_EPISODE), eq("NSRKK"), eq(9), any(Timestamp.class), any(Timestamp.class))).thenReturn(rows);
    }

    private void storedOpen(Boolean... isOpen) {
        when(jdbc.queryForList(startsWith(IS_OPEN), eq(Boolean.class), eq("NSRKK"), eq(9)))
                .thenReturn(isOpen.length == 0 ? Collections.emptyList() : Arrays.asList(isOpen));
    }

    @Test
    void firstRaiseOpensEpisodeAndIsSent() {
        touchOpen(RAISE_OPEN, 0);
        openEpisode(1);

        assertTrue(gate.shouldSend("NSRKK", 9, false, AT));
    }

    @Test
    void raiseDuringOpenEpisodeIsHeld() {
        touchOpen(RAISE_OPEN, 1);

        assertFalse(gate.shouldSend("NSRKK", 9, false, AT));
        verify(jdbc, never()).update(startsWith(OPEN_EPISODE), eq("NSRKK"), eq(9), any(Timestamp.class), any(Timestamp.class));
    }

    @Test
    void raiseLosingTheRaceOrOlderThanStateIsHeld() {
        touchOpen(RAISE_OPEN, 0);
        openEpisode(0);

        assertFalse(gate.shouldSend("NSRKK", 9, false, AT));
    }

    @Test
    void clearOfOpenEpisodeIsHeldUntilQuiet() {
        touchOpen(CLEAR_OPEN, 1);

        assertFalse(gate.shouldSend("NSRKK", 9, true, AT));
    }

    @Test
    void clearOlderThanTheEpisodesLastRaiseIsHeld() {
        touchOpen(CLEAR_OPEN, 0);
        storedOpen(true);

        assertFalse(gate.shouldSend("NSRKK", 9, true, AT));
    }

    @Test
    void clearWithoutEpisodeIsSentNow() {
        touchOpen(CLEAR_OPEN, 0);
        storedOpen();

        assertTrue(gate.shouldSend("NSRKK", 9, true, AT));
    }

    @Test
    void clearAfterEpisodeClosedIsSentNow() {
        touchOpen(CLEAR_OPEN, 0);
        storedOpen(false);

        assertTrue(gate.shouldSend("NSRKK", 9, true, AT));
    }

    @Test
    void databaseErrorSendsRatherThanDrops() {
        when(jdbc.update(anyString(), any(Timestamp.class), eq("NSRKK"), eq(9), any(Timestamp.class)))
                .thenThrow(new DataAccessResourceFailureException("down"));

        assertTrue(gate.shouldSend("NSRKK", 9, false, AT));
        assertTrue(gate.shouldSend("NSRKK", 9, true, AT));
    }

    @Test
    void legacyModeUsesThePerNodeThrottle() {
        ReflectionTestUtils.setField(gate, "mode", "legacy");
        when(legacy.shouldSendAlarm(anyString(), anyInt(), anyBoolean())).thenReturn(false);

        assertFalse(gate.shouldSend("NSRKK", 9, false, AT));
        verify(legacy).shouldSendAlarm("NSRKK", 9, false);
        assertTrue(gate.closeQuietEpisodes().isEmpty());
    }
}
