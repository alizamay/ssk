package dev.niceone.smartsiteappserver.initializer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Slf4j
@Component
public class SchedulerConfigInitializer {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void init() {
        createSchemaIfNotExists();
        createSchedulerConfigTable();
        insertDefaultConfigurations();
    }

    private void createSchemaIfNotExists() {
        try {
            String sql = "CREATE SCHEMA IF NOT EXISTS smartsite";
            jdbcTemplate.execute(sql);
            log.info("Schema 'smartsite' created or already exists");
        } catch (Exception e) {
            log.error("Error creating schema: {}", e.getMessage());
        }
    }

    private void createSchedulerConfigTable() {
        try {
            String sql = "CREATE TABLE IF NOT EXISTS smartsite.scheduler_config (" +
                    "id VARCHAR(255) PRIMARY KEY, " +
                    "config_key VARCHAR(100) NOT NULL UNIQUE, " +
                    "config_value VARCHAR(500), " +
                    "config_type VARCHAR(50), " +
                    "description VARCHAR(255), " +
                    "is_active BOOLEAN NOT NULL DEFAULT true, " +
                    "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "updated_by VARCHAR(100)" +
                    ")";
            
            jdbcTemplate.execute(sql);
            log.info("Table 'smartsite.scheduler_config' created or already exists");
            
            // Create index on config_key for faster lookups
            String indexSql = "CREATE INDEX IF NOT EXISTS idx_scheduler_config_key ON smartsite.scheduler_config(config_key)";
            jdbcTemplate.execute(indexSql);
            
        } catch (Exception e) {
            log.error("Error creating scheduler_config table: {}", e.getMessage());
        }
    }
    
    private void insertDefaultConfigurations() {
        try {
            // Check if table is empty
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM smartsite.scheduler_config", 
                Integer.class
            );
            
            if (count == 0) {
                log.info("Inserting default scheduler configurations...");
                
                // Insert default configurations (app.site_scheduler uses properties file only)
                String insertSql = "INSERT INTO smartsite.scheduler_config " +
                    "(id, config_key, config_value, config_type, description, is_active, created_at, updated_at, updated_by) " +
                    "VALUES " +
                    "(gen_random_uuid(), 'app.offline_check', 'true', 'BOOLEAN', 'Enable offline check scheduler', true, NOW(), NOW(), 'SYSTEM'), " +
                    "(gen_random_uuid(), 'app.online_check', 'true', 'BOOLEAN', 'Enable online check scheduler', true, NOW(), NOW(), 'SYSTEM'), " +
                    "(gen_random_uuid(), 'app.health_check_enabled', 'true', 'BOOLEAN', 'Enable daily health check scheduler', true, NOW(), NOW(), 'SYSTEM'), " +
                    "(gen_random_uuid(), 'app.site_status_corn', '0 0 4 * * *', 'CRON', 'Daily snapshot cron expression', true, NOW(), NOW(), 'SYSTEM'), " +
                    "(gen_random_uuid(), 'app.offline_check_corn', '0 5 4 * * *', 'CRON', 'Offline ping check cron expression', true, NOW(), NOW(), 'SYSTEM'), " +
                    "(gen_random_uuid(), 'app.online_check_corn', '0 10 4 * * *', 'CRON', 'Online ping check cron expression', true, NOW(), NOW(), 'SYSTEM'), " +
                    "(gen_random_uuid(), 'app.health_check_corn', '0 15 4 * * *', 'CRON', 'Health check cron expression', true, NOW(), NOW(), 'SYSTEM')";
                
                jdbcTemplate.execute(insertSql);
                log.info("Default scheduler configurations inserted successfully");
            } else {
                log.info("Scheduler configurations already exist, skipping default insert");
            }
        } catch (Exception e) {
            log.warn("Could not insert default configurations: {}", e.getMessage());
        }
    }
}