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
@Table(name = "sub_district")
public class SubDistrict extends AuditModel implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator="subdistrict_seq_gen")
    @SequenceGenerator(name="subdistrict_seq_gen", sequenceName="subdistrict_id_seq", allocationSize=1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "district_id", nullable = false)
    @OnDelete(action = OnDeleteAction.NO_ACTION)
    @JsonIgnore
    private District district;

    @NotEmpty(message = "Please provide name_th")
    private String nameTh;

    @Column(columnDefinition="boolean default true")
    private Boolean active;

    private String nameEn;

    public SubDistrict() {
    }

    public SubDistrict(District district, String nameTh, String nameEn, Boolean active) {
        this.district = district;
        this.nameTh = nameTh;
        this.nameEn = nameEn;
        this.active = active;
    }
}