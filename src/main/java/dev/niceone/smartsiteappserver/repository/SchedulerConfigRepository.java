package dev.niceone.smartsiteappserver.repository;

import dev.niceone.smartsiteappserver.model.SchedulerConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface SchedulerConfigRepository extends JpaRepository<SchedulerConfig, String> {
    
    /**
     * Find active config by key
     */
    @Query("SELECT sc FROM SchedulerConfig sc WHERE sc.configKey = :key AND sc.isActive = true")
    Optional<SchedulerConfig> findByConfigKeyAndIsActive(@Param("key") String configKey);
    
    /**
     * Find all active configs
     */
    @Query("SELECT sc FROM SchedulerConfig sc WHERE sc.isActive = true ORDER BY sc.configKey")
    List<SchedulerConfig> findAllActive();
    
    /**
     * Find configs by type
     */
    @Query("SELECT sc FROM SchedulerConfig sc WHERE sc.configType = :type AND sc.isActive = true")
    List<SchedulerConfig> findByConfigTypeAndIsActive(@Param("type") String configType);
    
    /**
     * Update config value by key
     */
    @Modifying
    @Transactional
    @Query("UPDATE SchedulerConfig sc SET sc.configValue = :value, sc.updatedAt = CURRENT_TIMESTAMP, sc.updatedBy = :updatedBy WHERE sc.configKey = :key AND sc.isActive = true")
    int updateConfigValue(@Param("key") String configKey, @Param("value") String configValue, @Param("updatedBy") String updatedBy);
    
    /**
     * Toggle boolean config
     */
    @Modifying
    @Transactional
    @Query("UPDATE SchedulerConfig sc SET sc.configValue = CASE WHEN sc.configValue = 'true' THEN 'false' ELSE 'true' END, sc.updatedAt = CURRENT_TIMESTAMP, sc.updatedBy = :updatedBy WHERE sc.configKey = :key AND sc.configType = 'BOOLEAN' AND sc.isActive = true")
    int toggleBooleanConfig(@Param("key") String configKey, @Param("updatedBy") String updatedBy);
    
    /**
     * Find scheduler cron configs
     */
    @Query("SELECT sc FROM SchedulerConfig sc WHERE sc.configKey IN ('app.site_status_corn', 'app.offline_check_corn', 'app.online_check_corn') AND sc.isActive = true")
    List<SchedulerConfig> findSchedulerCronConfigs();
    
    /**
     * Find scheduler toggle configs
     */
    @Query("SELECT sc FROM SchedulerConfig sc WHERE sc.configKey IN ('app.offline_check', 'app.online_check') AND sc.isActive = true")
    List<SchedulerConfig> findSchedulerToggleConfigs();
    
    /**
     * Check if config exists
     */
    @Query("SELECT COUNT(sc) > 0 FROM SchedulerConfig sc WHERE sc.configKey = :key")
    boolean existsByConfigKey(@Param("key") String configKey);
}