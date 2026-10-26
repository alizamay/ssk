package dev.niceone.smartsiteappserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import javax.persistence.*;
import javax.validation.constraints.NotEmpty;
import java.io.Serializable;

@Entity
@Getter @Setter
@Table(name = "province")
public class Province extends AuditModel implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator="province_seq_gen")
    @SequenceGenerator(name="province_seq_gen", sequenceName="province_id_seq", allocationSize=1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "region_id", nullable = false)
    @OnDelete(action = OnDeleteAction.NO_ACTION)
    @JsonIgnore
    private Region region;

    @NotEmpty(message = "Please provide code")
    private String code;

    @NotEmpty(message = "Please provide name_th")
    @Column(name = "name_th")
    private String nameTh;

    @Column(columnDefinition="boolean default true")
    private Boolean active;

    @Column(name = "name_en")
    private String nameEn;

    public Province() {
    }

    public Province(Region region, String code, String name_th, String name_en, Boolean active) {
        this.region = region;
        this.code = code;
        this.nameTh = name_th;
        this.nameEn = name_en;
        this.active = active;
    }
}