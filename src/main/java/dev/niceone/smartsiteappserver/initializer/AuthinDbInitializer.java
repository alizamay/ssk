package dev.niceone.smartsiteappserver.initializer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
public class AuthinDbInitializer {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initDatabaseAuthin() {
        try {
            // Check if table exists and is partitioned
            String checkQuery = "SELECT COUNT(*) FROM pg_tables WHERE schemaname = 'smartsite' AND tablename = 'authin_logs'";
            Integer count = jdbcTemplate.queryForObject(checkQuery, Integer.class);
            
            if (count > 0) {
                log.info("Table authin_logs already exists, skipping creation");
                System.out.println("✅ Authin table already exists, skipped initialization.");
                return;
            }
            
            // ✅ สร้าง parent table
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS smartsite.authin_logs (" +
                            "id BIGSERIAL, " +
                            "timestamp TIMESTAMP NOT NULL, " +
                            "site_code TEXT NOT NULL, " +
                            "url TEXT, " +
                            "message TEXT, " +
                            "is_clear BOOLEAN DEFAULT false" +
                            ") PARTITION BY RANGE (timestamp)"
            );

        // ✅ สร้างพาร์ติชันล่วงหน้า
        for (int year = 2025; year <= 2030; year++) {
            for (int month = 1; month <= 12; month++) {
                String startDate = String.format("%04d-%02d-01", year, month);
                LocalDate start = LocalDate.parse(startDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                LocalDate end = start.plusMonths(1);
                String tableName = String.format("authin_logs_%04d_%02d", year, month);

                String createPartition = "" +
                        "DO $$\n" +
                        "BEGIN\n" +
                        "   IF NOT EXISTS (\n" +
                        "       SELECT FROM pg_tables WHERE schemaname = 'smartsite' AND tablename = '" + tableName + "'\n" +
                        "   ) THEN\n" +
                        "       EXECUTE format(\n" +
                        "           'CREATE TABLE smartsite.%I PARTITION OF smartsite.authin_logs FOR VALUES FROM (%L) TO (%L)',\n" +
                        "           '" + tableName + "',\n" +
                        "           '" + start + "',\n" +
                        "           '" + end + "'\n" +
                        "       );\n" +
                        "   END IF;\n" +
                        "END\n" +
                        "$$;";

                jdbcTemplate.execute(createPartition);
            }
        }

        // ✅ Index
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_authin_logs_timestamp ON smartsite.authin_logs USING BRIN (timestamp)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_authin_logs_site_time ON smartsite.authin_logs (site_code, timestamp DESC)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_authin_logs_is_clear ON smartsite.authin_logs (is_clear)");

        // ✅ ตาราง config ราย site
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS smartsite.authin_config (" +
                        "id SERIAL PRIMARY KEY, " +
                        "site_code TEXT UNIQUE NOT NULL, " +
                        "is_disabled BOOLEAN DEFAULT false, " +
                        "daily_limit INTEGER, " +
                        "updated_at TIMESTAMP DEFAULT NOW()" +
                        ")"
        );

        // ✅ ตาราง config global
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS smartsite.authin_global_config (" +
                        "id SERIAL PRIMARY KEY, " +
                        "is_disabled BOOLEAN DEFAULT false, " +
                        "daily_limit INTEGER, " +
                        "updated_at TIMESTAMP DEFAULT NOW()" +
                        ")"
        );

        // ✅ Default row สำหรับ global config
        jdbcTemplate.execute(
                "INSERT INTO smartsite.authin_global_config (id, is_disabled, daily_limit) " +
                        "VALUES (1, false, 100) " +
                        "ON CONFLICT (id) DO NOTHING"
        );

        System.out.println("✅ Autin initialized with smartsite schema and partitioning.");
        
        } catch (Exception e) {
            log.error("Error initializing authentication database: {}", e.getMessage());
        }
    }
}
