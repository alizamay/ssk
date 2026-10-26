package dev.niceone.smartsiteappserver.initializer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class SiteEventResolveHistoryInitializer {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void init() {
        createSchemaIfNotExists();
        createSequenceIfNotExists();
        createMainTable();
       // createPartitionFunctionAndTrigger();
        System.out.println("✅ Initialized site_event_resolve_history with sequence and partitioning.");
    }

    private void createSchemaIfNotExists() {
        String sql = "CREATE SCHEMA IF NOT EXISTS smartsite;";
        jdbcTemplate.execute(sql);
    }

    private void createSequenceIfNotExists() {
        String sql = "CREATE SEQUENCE IF NOT EXISTS smartsite.site_event_resolve_history_id_seq START 1;";
        jdbcTemplate.execute(sql);
    }

    private void createMainTable() {
        String sql = ""
                + "CREATE TABLE IF NOT EXISTS smartsite.site_event_resolve_history ("
                + "    id BIGINT NOT NULL,"
                + "    log_id UUID NOT NULL,"
                + "    site_id TEXT NOT NULL,"
                + "    site_code TEXT NOT NULL,"
                + "    cabinet_id TEXT,"
                + "    cabinet_no TEXT,"
                + "    alarm_type INT NOT NULL,"
                + "    alarm_type_name TEXT,"
                + "    create_date TIMESTAMP NOT NULL,"
                + "    resolve_date TIMESTAMP NOT NULL,"
                + "    PRIMARY KEY (id, create_date)"
                + ") PARTITION BY RANGE (create_date);";
        jdbcTemplate.execute(sql);
    }

    private void createPartitionFunctionAndTrigger() {
        String functionSql = ""
                + "CREATE OR REPLACE FUNCTION smartsite.create_site_event_resolve_history_partition() RETURNS TRIGGER AS $$\n"
                + "DECLARE\n"
                + "    partition_name TEXT;\n"
                + "    start_date DATE;\n"
                + "    end_date DATE;\n"
                + "BEGIN\n"
                + "    start_date := NEW.create_date::date;\n"
                + "    end_date := start_date + INTERVAL '1 day';\n"
                + "    partition_name := 'smartsite.site_event_resolve_history_' || to_char(start_date, 'YYYY_MM_DD');\n"
                + "\n"
                + "    EXECUTE format('CREATE TABLE IF NOT EXISTS %I (\n"
                + "        LIKE smartsite.site_event_resolve_history INCLUDING ALL\n"
                + "    ) PARTITION OF smartsite.site_event_resolve_history\n"
                + "    FOR VALUES FROM (%L) TO (%L);', partition_name, start_date, end_date);\n"
                + "\n"
                + "    RETURN NEW;\n"
                + "END;\n"
                + "$$ LANGUAGE plpgsql;";

        String triggerSql = ""
                + "DROP TRIGGER IF EXISTS trg_create_partition ON smartsite.site_event_resolve_history;\n"
                + "CREATE TRIGGER trg_create_partition\n"
                + "BEFORE INSERT ON smartsite.site_event_resolve_history\n"
                + "FOR EACH ROW EXECUTE FUNCTION smartsite.create_site_event_resolve_history_partition();";

        jdbcTemplate.execute(functionSql);
        jdbcTemplate.execute(triggerSql);
    }
}
