package dev.niceone.smartsiteappserver.service;

import dev.niceone.smartsiteappserver.model.SchedulerConfig;
import dev.niceone.smartsiteappserver.repository.SchedulerConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@DependsOn("schedulerConfigInitializer")
public class SchedulerConfigService {
    
    @Autowired
    private SchedulerConfigRepository schedulerConfigRepository;
    
    /**
     * Initialize default configurations
     */
    @PostConstruct
    public void initializeDefaultConfigs() {
        try {
        log.info("Initializing default scheduler configurations...");

        // Default configurations
        Map<String, Object[]> defaultConfigs = new HashMap<>();
        // {configKey: [value, type, description]}
        defaultConfigs.put("app.site_status_corn", new Object[]{"0 */5 * * * ?", "CRON", "Site status check cron expression"});
        defaultConfigs.put("app.offline_check_corn", new Object[]{"0 */2 * * * ?", "CRON", "Offline check cron expression"});
        defaultConfigs.put("app.online_check_corn", new Object[]{"0 */1 * * * ?", "CRON", "Online check cron expression"});
        defaultConfigs.put("app.offline_check", new Object[]{"false", "BOOLEAN", "Enable/disable offline check scheduler"});
        defaultConfigs.put("app.online_check", new Object[]{"false", "BOOLEAN", "Enable/disable online check scheduler"});
        defaultConfigs.put("onsite.source", new Object[]{"db", "STRING", "Source for checking technician onsite: db | ttsm_api"});
        
        for (Map.Entry<String, Object[]> entry : defaultConfigs.entrySet()) {
            String key = entry.getKey();
            Object[] values = entry.getValue();
            
            if (!schedulerConfigRepository.existsByConfigKey(key)) {
                SchedulerConfig config = new SchedulerConfig(
                    key, 
                    (String) values[0], 
                    (String) values[1], 
                    (String) values[2]
                );
                config.setUpdatedBy("system");
                schedulerConfigRepository.save(config);
                log.info("Created default config: {} = {}", key, values[0]);
            }
        }

        log.info("Default scheduler configurations initialized");
        } catch (Exception e) {
            log.error("Failed to initialize default scheduler configs (runtime falls back to defaults): {}", e.getMessage());
        }
    }
    
    /**
     * Get config value by key
     */
    public String getConfigValue(String configKey, String defaultValue) {
        long now = System.currentTimeMillis();
        CachedConfig cached = configCache.get(configKey);
        if (cached == null || cached.expiresAt < now) {
            Optional<SchedulerConfig> config = schedulerConfigRepository.findByConfigKeyAndIsActive(configKey);
            cached = new CachedConfig(config.map(SchedulerConfig::getConfigValue).orElse(null), now + CONFIG_CACHE_TTL_MS);
            configCache.put(configKey, cached);
        }
        return cached.value != null ? cached.value : defaultValue;
    }

    /**
     * Short TTL cache: getConfigValue was hit on every /api/alarm askTTSM request (~500k/day/node).
     * Updates made on this node clear it immediately; other nodes pick changes up within the TTL.
     */
    private static final long CONFIG_CACHE_TTL_MS = 15_000;
    private final Map<String, CachedConfig> configCache = new ConcurrentHashMap<>();

    private static final class CachedConfig {
        final String value;
        final long expiresAt;

        CachedConfig(String value, long expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }
    }
    
    /**
     * Get boolean config value
     */
    public boolean getBooleanConfig(String configKey, boolean defaultValue) {
        String value = getConfigValue(configKey, String.valueOf(defaultValue));
        return Boolean.parseBoolean(value);
    }
    
    /**
     * Update config value
     */
    @Transactional
    public boolean updateConfig(String configKey, String configValue, String updatedBy) {
        try {
            int updated = schedulerConfigRepository.updateConfigValue(configKey, configValue, updatedBy);
            configCache.remove(configKey);
            if (updated > 0) {
                log.info("Updated config: {} = {} (by: {})", configKey, configValue, updatedBy);
                return true;
            } else {
                log.warn("Config not found or not updated: {}", configKey);
                return false;
            }
        } catch (Exception e) {
            log.error("Error updating config: {} = {}", configKey, configValue, e);
            return false;
        }
    }
    
    /**
     * Toggle boolean config
     */
    @Transactional
    public boolean toggleConfig(String configKey, String updatedBy) {
        try {
            int updated = schedulerConfigRepository.toggleBooleanConfig(configKey, updatedBy);
            configCache.remove(configKey);
            if (updated > 0) {
                log.info("Toggled config: {} (by: {})", configKey, updatedBy);
                return true;
            } else {
                log.warn("Config not found or not toggled: {}", configKey);
                return false;
            }
        } catch (Exception e) {
            log.error("Error toggling config: {}", configKey, e);
            return false;
        }
    }
    
    /**
     * Get all active configs as map
     */
    public Map<String, String> getAllConfigs() {
        List<SchedulerConfig> configs = schedulerConfigRepository.findAllActive();
        Map<String, String> configMap = new HashMap<>();
        
        for (SchedulerConfig config : configs) {
            configMap.put(config.getConfigKey(), config.getConfigValue());
        }
        
        return configMap;
    }
    
    /**
     * Get scheduler status
     */
    public Map<String, Object> getSchedulerStatus() {
        Map<String, Object> status = new HashMap<>();
        
        // Cron expressions
        status.put("site_status_corn", getConfigValue("app.site_status_corn", "0 */5 * * * ?"));
        status.put("offline_check_corn", getConfigValue("app.offline_check_corn", "0 */2 * * * ?"));
        status.put("online_check_corn", getConfigValue("app.online_check_corn", "0 */1 * * * ?"));
        
        // Boolean toggles
        status.put("offline_check", getBooleanConfig("app.offline_check", false));
        status.put("online_check", getBooleanConfig("app.online_check", false));
        
        return status;
    }
    
    /**
     * Emergency stop all schedulers
     */
    @Transactional
    public Map<String, Object> emergencyStopAll(String updatedBy) {
        Map<String, Object> result = new HashMap<>();
        int updated = 0;
        
        String[] booleanConfigs = {"app.offline_check", "app.online_check"};
        
        for (String configKey : booleanConfigs) {
            if (updateConfig(configKey, "false", updatedBy)) {
                updated++;
            }
        }
        
        result.put("success", updated > 0);
        result.put("message", "Emergency stop executed - " + updated + " schedulers disabled");
        result.put("updated", updated);
        result.put("timestamp", System.currentTimeMillis());
        
        log.warn("EMERGENCY STOP executed by: {} | Updated {} configs", updatedBy, updated);
        
        return result;
    }
    
    /**
     * Batch update configs
     */
    @Transactional
    public Map<String, Object> batchUpdateConfigs(Map<String, Object> configUpdates, String updatedBy) {
        Map<String, Object> result = new HashMap<>();
        Map<String, String> updated = new HashMap<>();
        Map<String, String> failed = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : configUpdates.entrySet()) {
            String key = entry.getKey();
            String value = String.valueOf(entry.getValue());
            
            if (updateConfig(key, value, updatedBy)) {
                updated.put(key, value);
            } else {
                failed.put(key, "Update failed");
            }
        }
        
        result.put("success", !updated.isEmpty());
        result.put("updated", updated);
        result.put("failed", failed);
        result.put("message", "Batch update completed: " + updated.size() + " updated, " + failed.size() + " failed");
        
        return result;
    }
}