package dev.niceone.smartsiteappserver.initializer;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
@RequiredArgsConstructor
public class SiteEventStatSummaryInitializer {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void init() {
        String createTableSql = "CREATE TABLE IF NOT EXISTS smartsite.site_event_stat_summary (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "log_id UUID NOT NULL, " +
                "site_id TEXT NOT NULL, " +
                "site_code TEXT NOT NULL, " +
                "cabinet_id TEXT, " +
                "cabinet_no TEXT, " +
                "alarm_type INT NOT NULL, " +
                "alarm_type_name TEXT, " +
                "date DATE NOT NULL, " +
                "count_create_event INT NOT NULL DEFAULT 0, " +
                "count_resolve_event INT NOT NULL DEFAULT 0" +
                ");";

        String indexSql = "CREATE INDEX IF NOT EXISTS idx_stat_summary_log_id ON smartsite.site_event_stat_summary(log_id);" +
                "CREATE INDEX IF NOT EXISTS idx_stat_summary_date ON smartsite.site_event_stat_summary(date);" +
                "CREATE INDEX IF NOT EXISTS idx_stat_summary_site_code ON smartsite.site_event_stat_summary(site_code);";

        jdbcTemplate.execute(createTableSql);
        jdbcTemplate.execute(indexSql);
        System.out.println("✅ site_event_stat_summary table initialized with monthly partitioning.");
    }
}
