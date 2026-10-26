package dev.niceone.smartsiteappserver.initializer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class SiteLogInitializer {
    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void init() {
        try {
            createMainTable();
            createMonthlyPartitionIfNotExists();
            System.out.println("✅ SiteLog initialized with smartsite schema and partitioning.");
        } catch (Exception e) {
            log.error("Error initializing site event log database: {}", e.getMessage());
        }
    }

    private void createMainTable() {
        // Check if table exists and is partitioned
        String checkQuery = "SELECT COUNT(*) FROM pg_tables WHERE schemaname = 'smartsite' AND tablename = 'site_event_log'";
        Integer count = jdbcTemplate.queryForObject(checkQuery, Integer.class);
        
        if (count > 0) {
            log.info("Table site_event_log already exists, skipping creation");
            System.out.println("✅ SiteLog table already exists, skipped initialization.");
            return;
        }
        
        String sql =
                "CREATE TABLE IF NOT EXISTS smartsite.site_event_log ("
                        + " id UUID NOT NULL,"
                        + " active BOOLEAN,"
                        + " alarm_type INTEGER,"
                        + " cabinet_id TEXT,"
                        + " device_id TEXT,"
                        + " resolve_date TIMESTAMP,"
                        + " site_id TEXT,"
                        + " site_code TEXT,"
                        + " site_name_th TEXT,"
                        + " site_name_en TEXT,"
                        + " create_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                        + " update_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                        + " write_to_file BOOLEAN,"
                        + " alarm_type_code TEXT,"
                        + " alarm_type_name TEXT,"
                        + " cabinet_no TEXT,"
                        + " cabinet_code TEXT,"
                        + " lock_event_id INTEGER,"
                        + " lock_event_name TEXT,"
                        + " door_event_id INTEGER,"
                        + " door_event_name TEXT,"
                        + " status_code TEXT,"
                        + " region_code TEXT,"
                        + " region_name TEXT,"
                        + " is_resolve BOOLEAN DEFAULT FALSE,"
                        + " PRIMARY KEY (id, create_date)"
                        + ") PARTITION BY RANGE (create_date);";

        jdbcTemplate.execute(sql);
    }

    private void createMonthlyPartitionIfNotExists() {
        int currentYear = java.time.LocalDate.now().getYear();
        int currentMonth = java.time.LocalDate.now().getMonthValue();

        for (int year = currentYear; year <= currentYear + 5; year++) {
            for (int month = 1; month <= 12; month++) {
                // ข้ามเดือนที่อยู่ก่อนเดือนปัจจุบันในปีนี้
                if (year == currentYear && month < currentMonth) continue;

                String partitionName = "smartsite.site_event_log_" + year + "_" + String.format("%02d", month);
                LocalDate startDate = LocalDate.of(year, month, 1);
                LocalDate endDate = startDate.plusMonths(1);

                String sql = "CREATE TABLE IF NOT EXISTS " + partitionName +
                        " PARTITION OF smartsite.site_event_log " +
                        "FOR VALUES FROM ('" + startDate + "') TO ('" + endDate + "');";

                jdbcTemplate.execute(sql);
            }
        }
    }
}
