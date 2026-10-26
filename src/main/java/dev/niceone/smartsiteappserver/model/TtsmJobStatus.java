package dev.niceone.smartsiteappserver.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity สำหรับเก็บข้อมูล Job Status จาก TTSM
 * บันทึก 1 record ต่อ 1 siteCode
 */
@Entity
@Table(name = "ttsm_job_status", indexes = {
    @Index(name = "idx_ttsm_site_code", columnList = "site_code"),
    @Index(name = "idx_ttsm_job_id", columnList = "job_id"),
    @Index(name = "idx_ttsm_job_status", columnList = "job_status"),
    @Index(name = "idx_ttsm_received_at", columnList = "received_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TtsmJobStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "site_code", nullable = false, length = 50)
    private String siteCode;

    @Column(name = "job_id", length = 100)
    private String jobId;

    @Column(name = "job_status", length = 50)
    private String jobStatus;  // "On-Site", "Done", "Held"

    @Column(name = "action_date")
    private LocalDateTime actionDate;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @PrePersist
    protected void onCreate() {
        if (receivedAt == null) {
            receivedAt = LocalDateTime.now();
        }
    }
}
