package dev.niceone.smartsiteappserver.initializer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlarmNotifyStateInitializer {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void init() {
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS smartsite.alarm_notify_state (" +
                    "site_code     VARCHAR(50) NOT NULL, " +
                    "alarm_type    INTEGER NOT NULL, " +
                    "is_open       BOOLEAN NOT NULL, " +
                    "last_state    VARCHAR(10) NOT NULL, " +
                    "opened_at     TIMESTAMP, " +
                    "last_event_at TIMESTAMP NOT NULL, " +
                    "closed_at     TIMESTAMP, " +
                    "PRIMARY KEY (site_code, alarm_type))");
            // Used every minute to find episodes whose clear is due
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_alarm_notify_state_open " +
                    "ON smartsite.alarm_notify_state (last_event_at) WHERE is_open");
            log.info("Table 'smartsite.alarm_notify_state' created or already exists");
        } catch (Exception e) {
            log.error("Error initializing alarm_notify_state table: {}", e.getMessage());
        }
    }
}
