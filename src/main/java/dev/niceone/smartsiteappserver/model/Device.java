package dev.niceone.smartsiteappserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.niceone.smartsiteappserver.model.constant.DeviceTypeCodeEnum;
import dev.niceone.smartsiteappserver.model.constant.EventTypeCodeEnum;
import dev.niceone.smartsiteappserver.model.constant.StatusCodeEnum;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "device")
@Getter @Setter
public class Device extends AuditModel implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator="devices_seq_gen")
    @SequenceGenerator(name="devices_seq_gen", sequenceName="devices_id_seq", allocationSize=1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cabinet_id", nullable = false)
    @OnDelete(action = OnDeleteAction.NO_ACTION)
    @JsonIgnore
    private Cabinet cabinet;

    private DeviceTypeCodeEnum deviceTypeCode;
    private EventTypeCodeEnum eventType;

//    @ManyToOne(fetch = FetchType.LAZY, optional=false)
//    @JoinColumn(name="statusId", nullable=false)
//    @OnDelete(action = OnDeleteAction.NO_ACTION)
//    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
//    private Status status;
    private StatusCodeEnum status;

    private LocalDateTime installed_date;

    @Column(columnDefinition="boolean default true")
    private Boolean is_install;

    @Column(columnDefinition="boolean default true")
    private Boolean is_enable;

    @Column(columnDefinition="boolean default true")
    private Boolean active = true;

    private String name;
    private String serialnum;

    public Device() {
    }

    public Device(DeviceTypeCodeEnum type, Boolean is_install, Boolean is_enable, StatusCodeEnum status) {
        this.deviceTypeCode = type;
        this.is_install = is_install;
        this.is_enable = is_enable;
        this.status = status;
    }

    public Device(Cabinet cabinet, DeviceTypeCodeEnum type, Boolean is_install, Boolean is_enable, StatusCodeEnum status) {
        this.cabinet = cabinet;
        this.deviceTypeCode = type;
        this.is_install = is_install;
        this.is_enable = is_enable;
        this.status = status;
    }
}