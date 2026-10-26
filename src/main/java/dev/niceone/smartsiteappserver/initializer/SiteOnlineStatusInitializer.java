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
public class SiteOnlineStatusInitializer {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String SCHEMA = "smartsite";

    private static final String LEGACY_UQ = "uq_site_online_status_log_code_period_date";

    @PostConstruct
    public void init() {
        runStep("createSchema", this::createSchemaIfNotExists);

        boolean existed = tableExists();
        if (!existed) {
            // Fresh DB: create as partitioned table with monthly partitions
            runStep("createParentTable", this::createParentTable);
            runStep("createMonthlyPartitions", this::createMonthlyPartitions);
        } else {
            // Existing table: ensure port column, then handle by table kind
            runStep("addPortColumn", this::addPortColumnIfNotExists);
            if (isPartitioned()) {
                runStep("createMonthlyPartitions", this::createMonthlyPartitions);
            } else {
                // Legacy non-partitioned table (e.g. restored prod DB):
                // ensure the unique index that INSERT ... ON CONFLICT requires
                runStep("migrateLegacyTable", this::migrateLegacyTable);
            }
        }

        runStep("createIndexes", this::createIndexes);
        System.out.println("✅ SiteOnlineStatus initializer finished.");
    }

    /** Run one init step in isolation so a single failure does not abort the rest. */
    private void runStep(String name, Runnable step) {
        try {
            step.run();
        } catch (Exception e) {
            log.error("SiteOnlineStatus init step '{}' failed: {}", name, e.getMessage());
        }
    }

    private boolean tableExists() {
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_tables WHERE schemaname = '" + SCHEMA +
                        "' AND tablename = 'site_online_status_log'", Integer.class);
        return c != null && c > 0;
    }

    private boolean isPartitioned() {
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_partitioned_table p " +
                        "JOIN pg_class cl ON cl.oid = p.partrelid " +
                        "JOIN pg_namespace n ON n.oid = cl.relnamespace " +
                        "WHERE n.nspname = '" + SCHEMA + "' AND cl.relname = 'site_online_status_log'",
                Integer.class);
        return c != null && c > 0;
    }

    /**
     * Self-heal a legacy non-partitioned site_online_status_log so that
     * INSERT ... ON CONFLICT (site_code, period, (check_time::date)) works.
     * Idempotent: only runs the heavy dedupe/index build once (when the unique index is missing).
     */
    private void migrateLegacyTable() {
        Integer idx = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = '" + SCHEMA +
                        "' AND tablename = 'site_online_status_log' AND indexname = '" + LEGACY_UQ + "'",
                Integer.class);
        if (idx != null && idx > 0) {
            return; // already migrated
        }

        log.warn("Legacy non-partitioned site_online_status_log detected - migrating (dedupe + unique index)");

        int deleted = jdbcTemplate.update(
                "DELETE FROM " + SCHEMA + ".site_online_status_log a " +
                        "USING " + SCHEMA + ".site_online_status_log b " +
                        "WHERE a.site_code = b.site_code AND a.period = b.period " +
                        "AND a.check_time::date = b.check_time::date AND a.id < b.id");
        log.warn("Legacy migrate: removed {} duplicate row(s)", deleted);

        jdbcTemplate.execute(
                "CREATE UNIQUE INDEX IF NOT EXISTS " + LEGACY_UQ + " ON " + SCHEMA +
                        ".site_online_status_log (site_code, period, (check_time::date))");
        log.warn("Legacy migrate: created unique index {}", LEGACY_UQ);
    }

    private void createSchemaIfNotExists() {
        String sql = "CREATE SCHEMA IF NOT EXISTS " + SCHEMA;
        jdbcTemplate.execute(sql);
    }

    private void createParentTable() {
        // Check if table exists
        String checkQuery = "SELECT COUNT(*) FROM pg_tables WHERE schemaname = 'smartsite' AND tablename = 'site_online_status_log'";
        Integer count = jdbcTemplate.queryForObject(checkQuery, Integer.class);
        
        if (count > 0) {
            log.info("Table site_online_status_log already exists, skipping creation");
            System.out.println("✅ SiteOnlineStatus table already exists, skipped initialization.");
            return;
        }
        
        String sql = "CREATE TABLE IF NOT EXISTS " + SCHEMA + ".site_online_status_log (" +
                "id SERIAL NOT NULL, " +
                "site_code TEXT NOT NULL, " +
                "ip_address TEXT, " +
                "default_gateway TEXT, " +
                "status_ping_ip BOOLEAN, " +
                "status_ping_gateway BOOLEAN, " +
                "status_call_helpcheck BOOLEAN, " +
                "is_ping_ip BOOLEAN, " +
                "is_ping_gateway BOOLEAN, " +
                "is_call_helpcheck BOOLEAN, " +
                "json_result TEXT, " +
                "port INTEGER, " +
                "status BOOLEAN NOT NULL, " +
                "period TIME NOT NULL, " +
                "check_time TIMESTAMP NOT NULL" +
                ") PARTITION BY RANGE (check_time)";
        jdbcTemplate.execute(sql);
    }

    private void createMonthlyPartitions() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy_MM");
        LocalDate now = LocalDate.now();
        int startYear = now.getYear();
        int endYear = startYear + 5;  // สร้างล่วงหน้า 5 ปี

        for (int year = startYear; year <= endYear; year++) {
            for (int month = 1; month <= 12; month++) {
                LocalDate start = LocalDate.of(year, month, 1);
                LocalDate end = start.plusMonths(1);

                String partitionName = SCHEMA + ".site_online_status_log_" + fmt.format(start);
                String sql = String.format(
                        "CREATE TABLE IF NOT EXISTS %s PARTITION OF %s.site_online_status_log " +
                                "FOR VALUES FROM ('%s') TO ('%s')",
                        partitionName, SCHEMA,
                        start.toString(), end.toString()
                );
                jdbcTemplate.execute(sql);

                // สร้าง index บนแต่ละ partition เพื่อช่วย join และ filter เร็วขึ้น
                String indexName = partitionName.replace('.', '_') + "_site_code_check_time_idx";
                String indexSql = String.format(
                        "CREATE INDEX IF NOT EXISTS %s ON %s (site_code, check_time)",
                        indexName, partitionName
                );
                jdbcTemplate.execute(indexSql);

                // สร้าง unique index เพื่อป้องกัน 1 site มีหลาย row ในวันเดียวกัน + period เดียวกัน
                String uniqueIndexName = partitionName.replace('.', '_') + "_site_period_date_uq";
                String uniqueIndexSql = String.format(
                        "CREATE UNIQUE INDEX IF NOT EXISTS %s ON %s (site_code, period, (check_time::date))",
                        uniqueIndexName, partitionName
                );
                jdbcTemplate.execute(uniqueIndexSql);
            }
        }
    }
    private void addPortColumnIfNotExists() {
        String checkColumn = "SELECT COUNT(*) FROM information_schema.columns " +
                "WHERE table_schema = 'smartsite' AND table_name = 'site_online_status_log' AND column_name = 'port'";
        Integer count = jdbcTemplate.queryForObject(checkColumn, Integer.class);
        if (count == null || count == 0) {
            jdbcTemplate.execute("ALTER TABLE " + SCHEMA + ".site_online_status_log ADD COLUMN port INTEGER");
            log.info("Added 'port' column to site_online_status_log");
        }
    }

    private void createIndexes() {
        String index1 = "CREATE INDEX IF NOT EXISTS idx_status_log_site_code " +
                "ON " + SCHEMA + ".site_online_status_log(site_code)";
        String index2 = "CREATE INDEX IF NOT EXISTS idx_status_log_check_time " +
                "ON " + SCHEMA + ".site_online_status_log(check_time)";
        String index3 = "CREATE INDEX IF NOT EXISTS idx_status_log_period " +
                "ON " + SCHEMA + ".site_online_status_log(period)";
        String index4 = "CREATE INDEX IF NOT EXISTS idx_status_log_status " +
                "ON " + SCHEMA + ".site_online_status_log(status)";
        String index5 = "CREATE INDEX IF NOT EXISTS idx_status_log_site_period_status " +
                "ON " + SCHEMA + ".site_online_status_log(site_code, check_time DESC, period, status)";

        jdbcTemplate.execute(index1);
        jdbcTemplate.execute(index2);
        jdbcTemplate.execute(index3);
        jdbcTemplate.execute(index4);
        jdbcTemplate.execute(index5); // composite index for the DISTINCT ON query
    }
}