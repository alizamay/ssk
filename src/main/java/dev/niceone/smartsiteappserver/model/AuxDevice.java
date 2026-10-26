package dev.niceone.smartsiteappserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import javax.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "aux_device")
@Getter
@Setter
public class AuxDevice extends AuditModel implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator="aux_devices_seq_gen")
    @SequenceGenerator(name="aux_devices_seq_gen", sequenceName="aux_devices_id_seq", allocationSize=1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cabinet_id", nullable = false)
    @OnDelete(action = OnDeleteAction.NO_ACTION)
    @JsonIgnore
    private Cabinet cabinet;

    @Column(name="aux_no")
    private int auxNo;

    private String name;

    @Column(columnDefinition="boolean default true")
    private Boolean is_input = true;

    @Column(columnDefinition="boolean default true")
    private Boolean is_on = true;

    @Column(columnDefinition="boolean default true")
    private Boolean is_active = true;

    public AuxDevice() {
    }

    public AuxDevice(int auxNo, boolean is_input) {
        this.auxNo = auxNo;
        this.is_input = is_input;
        this.name = "";
    }

    public AuxDevice(int auxNo, boolean is_input, String name) {
        this.auxNo = auxNo;
        this.is_input = is_input;
        this.name = name;
    }
}
