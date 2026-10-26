package dev.niceone.smartsiteappserver.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "scheduler_lock", schema = "smartsite")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SchedulerLock {

    @Id
    @Column(name = "lock_name", length = 50)
    private String lockName;

    // UUID ที่ generate ตอน node startup — unique ต่อ process
    @Column(name = "node_id", nullable = false, length = 100)
    private String nodeId;

    @Column(name = "acquired_at", nullable = false)
    private LocalDateTime acquiredAt;

    @Column(name = "heartbeat_at", nullable = false)
    private LocalDateTime heartbeatAt;
}
