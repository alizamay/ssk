package dev.niceone.smartsiteappserver.model;

import javax.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "site_event_resolve_history", schema = "smartsite")
@IdClass(SiteEventResolveHistoryId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SiteEventResolveHistory {

    @Id
    @Column(name = "id", nullable = false)
    private Long id;  // กำหนดเอง (ต้องใช้ sequence ดึงค่าใน service)

    @Id
    @Column(name = "create_date", nullable = false)
    private LocalDateTime createDate;

    @Column(name = "log_id", nullable = false)
    private UUID logId;

    @Column(name = "site_id", nullable = false)
    private String siteId;

    @Column(name = "site_code", nullable = false)
    private String siteCode;

    @Column(name = "cabinet_id")
    private String cabinetId;

    @Column(name = "cabinet_no")
    private String cabinetNo;

    @Column(name = "alarm_type", nullable = false)
    private Integer alarmType;

    @Column(name = "alarm_type_name")
    private String alarmTypeName;

    @Column(name = "resolve_date", nullable = false)
    private LocalDateTime resolveDate;
}