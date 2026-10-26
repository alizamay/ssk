package dev.niceone.smartsiteappserver.model;

import javax.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "site_event_stat_summary", schema = "smartsite")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SiteEventStatSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "count_create_event", nullable = false)
    private Integer countCreateEvent;

    @Column(name = "count_resolve_event", nullable = false)
    private Integer countResolveEvent;

}
