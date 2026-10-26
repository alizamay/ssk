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
@Table(name = "district")
public class District extends AuditModel implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator="district_seq_gen")
    @SequenceGenerator(name="district_seq_gen", sequenceName="district_id_seq", allocationSize=1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "province_id", nullable = false)
    @OnDelete(action = OnDeleteAction.NO_ACTION)
    @JsonIgnore
    private Province province;

    private Integer zipcode;

    @NotEmpty(message = "Please provide name_th")
    @Column(name = "name_th")
    private String nameTh;

    @Column(columnDefinition="boolean default true")
    private Boolean active;

    @Column(name = "name_en")
    private String nameEn;

    public District() {
    }

    public District(Province province, Integer zipcode, String name_th, String name_en, Boolean active) {
        this.province = province;
        this.zipcode = zipcode;
        this.nameTh = name_th;
        this.nameEn = name_en;
        this.active = active;
    }
}