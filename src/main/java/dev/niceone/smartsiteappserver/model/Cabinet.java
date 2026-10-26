package dev.niceone.smartsiteappserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.niceone.smartsiteappserver.model.constant.DoorEventCodeEnum;
import dev.niceone.smartsiteappserver.model.constant.EventTypeCodeEnum;
import dev.niceone.smartsiteappserver.model.constant.LockEventCodeEnum;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "cabinet")
@Getter
@Setter
public class Cabinet extends AuditModel implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator="cabinet_seq_gen")
    @SequenceGenerator(name="cabinet_seq_gen", sequenceName="cabinet_id_seq", allocationSize=1)
    private Long id;

    private String code;

    @Column(name="cabinet_no")
    private int cabinetNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    @OnDelete(action = OnDeleteAction.NO_ACTION)
    @JsonIgnore
    private Site site;

    private String mac_address;

    @Column(columnDefinition="boolean default false")
    private Boolean online = false;

    private LockEventCodeEnum lockEventCode;
    private DoorEventCodeEnum doorEventCode;

//    private EventTypeCodeEnum lockEventType;
//    private EventTypeCodeEnum doorEventType;

    private LocalDateTime installed_date;

    @Column(columnDefinition="boolean default true")
    private Boolean active = true;

    @Column(columnDefinition="boolean default true")
    private Boolean auxin1 = true;

    @Column(columnDefinition="boolean default true")
    private Boolean auxin2 = true;

    @Column(columnDefinition="boolean default true")
    private Boolean auxin3 = true;

    @Column(columnDefinition="boolean default true")
    private Boolean auxout = true;

    @Column(name="json_devices")
    private String jsonDevices;
    public Cabinet() {
    }

    public Cabinet(String cabinet_code, int cabinet_no, boolean active, LockEventCodeEnum lockEventCode, DoorEventCodeEnum doorEventCode,String jsonDevices) {
        this.code = cabinet_code;
        this.cabinetNo = cabinet_no;
        this.active = active;
        this.lockEventCode = lockEventCode;
        this.doorEventCode = doorEventCode;
        this.jsonDevices = jsonDevices;
    }

    public Cabinet(Site site, String cabinet_code, int cabinet_no, boolean active, LockEventCodeEnum lockEventCode, DoorEventCodeEnum doorEventCode,String jsonDevices) {
        this.site = site;
        this.code = cabinet_code;
        this.cabinetNo = cabinet_no;
        this.active = active;
        this.lockEventCode = lockEventCode;
        this.doorEventCode = doorEventCode;
        this.jsonDevices = jsonDevices;
    }

    @OneToMany(
            mappedBy = "cabinet",
            cascade = CascadeType.ALL,
            fetch = FetchType.LAZY,
            orphanRemoval = true
    )
    @Fetch(FetchMode.SELECT)
    private List<Device> devices = new ArrayList<>();

    @OneToMany(
            mappedBy = "cabinet",
            cascade = CascadeType.ALL,
            fetch = FetchType.LAZY,
            orphanRemoval = true
    )
    @Fetch(FetchMode.SELECT)
    private List<AuxDevice> aux_devices = new ArrayList<>();

    public void addDevice(Device device) {
        devices.add(device);
        device.setCabinet(this);
    }

    public void addAuxDevice(AuxDevice aux_device) {
        aux_devices.add(aux_device);
        aux_device.setCabinet(this);
    }
}