package dev.niceone.smartsiteappserver.initializer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Slf4j
@Component
@RequiredArgsConstructor
public class TtsmJobStatusInitializer {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void init() {
        try {
            String checkQuery = "SELECT COUNT(*) FROM pg_tables WHERE schemaname = 'smartsite' AND tablename = 'ttsm_job_status'";
            Integer count = jdbcTemplate.queryForObject(checkQuery, Integer.class);

            if (count != null && count > 0) {
                log.info("Table ttsm_job_status already exists, skipping creation");
                System.out.println("✅ ttsm_job_status table already exists, skipped initialization.");
                return;
            }

            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS smartsite.ttsm_job_status (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "site_code VARCHAR(50) NOT NULL, " +
                "job_id VARCHAR(100), " +
                "job_status VARCHAR(50), " +
                "action_date TIMESTAMP, " +
                "received_at TIMESTAMP NOT NULL" +
                ")"
            );

            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_ttsm_site_code   ON smartsite.ttsm_job_status (site_code)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_ttsm_job_id      ON smartsite.ttsm_job_status (job_id)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_ttsm_job_status  ON smartsite.ttsm_job_status (job_status)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_ttsm_received_at ON smartsite.ttsm_job_status (received_at)");

            System.out.println("✅ ttsm_job_status initialized.");
        } catch (Exception e) {
            log.error("Error initializing ttsm_job_status table: {}", e.getMessage());
        }
    }
}
