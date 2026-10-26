package dev.niceone.smartsiteappserver.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "onsite_check_log", schema = "smartsite", indexes = {
    @Index(name = "idx_onsite_check_log_site_code", columnList = "site_code"),
    @Index(name = "idx_onsite_check_log_request_time", columnList = "request_time")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OnsiteCheckLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "site_code", nullable = false, length = 50)
    private String siteCode;

    @Column(name = "client_ip", length = 100)
    private String clientIp;

    @Column(name = "request_time", nullable = false)
    private LocalDateTime requestTime;

    // 0 = ไม่มีข้อมูล, 1 = ช่างอยู่ onsite, 2 = ช่างไม่อยู่
    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_raw", length = 200)
    private String responseRaw;

    @PrePersist
    protected void onCreate() {
        if (requestTime == null) {
            requestTime = LocalDateTime.now();
        }
    }
}
