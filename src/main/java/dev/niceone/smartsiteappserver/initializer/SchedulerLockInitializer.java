package dev.niceone.smartsiteappserver.initializer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Slf4j
@Component
@RequiredArgsConstructor
public class SchedulerLockInitializer {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void init() {
        try {
            createSchedulerLockTable();
            log.info("SchedulerLock initialized");
        } catch (Exception e) {
            log.error("Error initializing scheduler_lock table: {}", e.getMessage());
        }
    }

    private void createSchedulerLockTable() {
        String sql = "CREATE TABLE IF NOT EXISTS smartsite.scheduler_lock (" +
                "lock_name    VARCHAR(50) PRIMARY KEY, " +
                "node_id      VARCHAR(100) NOT NULL, " +
                "acquired_at  TIMESTAMP NOT NULL, " +
                "heartbeat_at TIMESTAMP NOT NULL" +
                ")";
        jdbcTemplate.execute(sql);
        log.info("Table 'smartsite.scheduler_lock' created or already exists");
    }
}
