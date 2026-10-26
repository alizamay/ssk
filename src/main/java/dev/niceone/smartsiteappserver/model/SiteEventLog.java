package dev.niceone.smartsiteappserver.model;

import javax.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "site_event_log", schema = "smartsite")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(SiteEventLogId.class) // ใช้ composite key id + createDate
public class SiteEventLog {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Id
    @Column(name = "create_date", nullable = false)
    private LocalDateTime createDate;

    @Column(name = "active")
    private Boolean active;

    @Column(name = "alarm_type")
    private Integer alarmType;

    @Column(name = "cabinet_id")
    private String cabinetId;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "resolve_date")
    private LocalDateTime resolveDate;

    @Column(name = "site_id")
    private String siteId;

    @Column(name = "site_code")
    private String siteCode;

    @Column(name = "site_name_th")
    private String siteNameTh;

    @Column(name = "site_name_en")
    private String siteNameEn;

    @Column(name = "update_date")
    private LocalDateTime updateDate;

    @Column(name = "write_to_file")
    private Boolean writeToFile;

    @Column(name = "alarm_type_code")
    private String alarmTypeCode;

    @Column(name = "alarm_type_name")
    private String alarmTypeName;

    @Column(name = "cabinet_no")
    private String cabinetNo;

    @Column(name = "cabinet_code")
    private String cabinetCode;

    @Column(name = "lock_event_id")
    private Integer lockEventId;

    @Column(name = "lock_event_name")
    private String lockEventName;

    @Column(name = "door_event_id")
    private Integer doorEventId;

    @Column(name = "door_event_name")
    private String doorEventName;

    @Column(name = "status_code")
    private String statusCode;

    @Column(name = "region_code")
    private String regionCode;

    @Column(name = "region_name")
    private String regionName;

    @Column(name = "is_resolve")
    private Boolean isResolve;

}
