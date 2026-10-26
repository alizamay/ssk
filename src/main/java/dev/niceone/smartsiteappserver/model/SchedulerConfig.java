package dev.niceone.smartsiteappserver.model;

import lombok.Data;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "scheduler_config", schema = "smartsite")
@Data
public class SchedulerConfig {
    
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private String id;
    
    @Column(name = "config_key", nullable = false, unique = true, length = 100)
    private String configKey;
    
    @Column(name = "config_value", length = 500)
    private String configValue;
    
    @Column(name = "config_type", length = 50)
    private String configType; // BOOLEAN, CRON, STRING
    
    @Column(name = "description", length = 255)
    private String description;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "updated_by", length = 100)
    private String updatedBy;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Constructors
    public SchedulerConfig() {}
    
    public SchedulerConfig(String configKey, String configValue, String configType, String description) {
        this.configKey = configKey;
        this.configValue = configValue;
        this.configType = configType;
        this.description = description;
        this.isActive = true;
    }
    
    // Helper methods
    public boolean getBooleanValue() {
        if (configValue == null) return false;
        return Boolean.parseBoolean(configValue);
    }
    
    public void setBooleanValue(boolean value) {
        this.configValue = String.valueOf(value);
    }
}