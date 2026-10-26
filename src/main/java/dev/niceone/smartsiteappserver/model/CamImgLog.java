package dev.niceone.smartsiteappserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import javax.persistence.*;
import java.io.File;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

@Getter
@Setter
@Entity
@Table(name = "cam_img_log")
public class CamImgLog extends AuditModel implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator="camimglog_seq_gen")
    @SequenceGenerator(name="camimglog_seq_gen", sequenceName="camimglog_id_seq", allocationSize=1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "alarm_log_id", nullable = true)
    @OnDelete(action = OnDeleteAction.NO_ACTION)
    @JsonIgnore
    private AlarmLog alarmLog;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "site_id", nullable = true)
    @OnDelete(action = OnDeleteAction.NO_ACTION)
    @JsonIgnore
    private Site site;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "cabinet_id", nullable = true)
    @OnDelete(action = OnDeleteAction.NO_ACTION)
    @JsonIgnore
    private Cabinet cabinet;

    private String img_path;

    private String img_filename;

    @Column(columnDefinition="boolean default true")
    private Boolean active;

    public CamImgLog() {
    }

    public CamImgLog(AlarmLog alarmLog, Site site, Cabinet cabinet, String img_path, String img_filename) {
        this.alarmLog = alarmLog;
        this.site = site;
        this.cabinet = cabinet;
        this.img_path = img_path;
        this.img_filename = img_filename;
        this.active = true;
    }
}