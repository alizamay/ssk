package dev.niceone.smartsiteappserver.initializer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Slf4j
@Component
@RequiredArgsConstructor
public class OnsiteCheckLogInitializer {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void init() {
        try {
            createOnsiteCheckLogTable();
            log.info("OnsiteCheckLog initialized");
        } catch (Exception e) {
            log.error("Error initializing onsite_check_log table: {}", e.getMessage());
        }
    }

    private void createOnsiteCheckLogTable() {
        String sql = "CREATE TABLE IF NOT EXISTS smartsite.onsite_check_log (" +
                "id              BIGSERIAL PRIMARY KEY, " +
                "site_code       VARCHAR(50) NOT NULL, " +
                "client_ip       VARCHAR(100), " +
                "request_time    TIMESTAMP NOT NULL, " +
                "response_status INTEGER, " +
                "response_raw    VARCHAR(200)" +
                ")";
        jdbcTemplate.execute(sql);

        jdbcTemplate.execute(
            "CREATE INDEX IF NOT EXISTS idx_onsite_check_log_site_code " +
            "ON smartsite.onsite_check_log(site_code)"
        );
        jdbcTemplate.execute(
            "CREATE INDEX IF NOT EXISTS idx_onsite_check_log_request_time " +
            "ON smartsite.onsite_check_log(request_time)"
        );

        log.info("Table 'smartsite.onsite_check_log' created or already exists");
    }
}
