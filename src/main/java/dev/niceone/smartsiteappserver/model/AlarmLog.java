package dev.niceone.smartsiteappserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.niceone.smartsiteappserver.model.constant.AlarmTypeCodeEnum;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Getter @Setter
@Table(name = "alarm_log")
public class AlarmLog extends AuditModel implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator="alarmlog_seq_gen")
    @SequenceGenerator(name="alarmlog_seq_gen", sequenceName="alarmlog_id_seq", allocationSize=1)
    private Long id;

    private AlarmTypeCodeEnum alarmType;

    @Fetch(FetchMode.JOIN)
    @ManyToOne(fetch = FetchType.EAGER, optional = true)
    @JoinColumn(name = "site_id", nullable = true)
    @OnDelete(action = OnDeleteAction.NO_ACTION)
    @JsonIgnore
    private Site site;

    @Fetch(FetchMode.JOIN)
    @ManyToOne(fetch = FetchType.EAGER, optional = true)
    @JoinColumn(name = "cabinet_id", nullable = true)
    @OnDelete(action = OnDeleteAction.NO_ACTION)
    @JsonIgnore
    private Cabinet cabinet;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "device_id", nullable = true)
    @OnDelete(action = OnDeleteAction.NO_ACTION)
    @JsonIgnore
    private Device device;

    private LocalDateTime resolveDate;

    @Column(columnDefinition="boolean default true")
    private Boolean active;

    @Column(columnDefinition="boolean default false")
    private Boolean writeToFile;

    public AlarmLog() {
    }

    public AlarmLog(AlarmTypeCodeEnum alarmType, Site site, Cabinet cabinet, Device device, LocalDateTime resolveDate) {
        this.alarmType = alarmType;
        this.site = site;
        this.cabinet = cabinet;
        this.device = device;
        this.resolveDate = resolveDate;
        this.active = true;
        this.writeToFile = false;
    }
}