package dev.niceone.smartsiteappserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.niceone.smartsiteappserver.model.constant.StatusCodeEnum;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.web.multipart.MultipartFile;

import javax.persistence.*;
import javax.validation.constraints.NotEmpty;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "site")
@Getter
@Setter
public class Site extends AuditModel implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator="site_seq_gen")
    @SequenceGenerator(name="site_seq_gen", sequenceName="site_id_seq", allocationSize=1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional=false)
    @JoinColumn(name="region_id", nullable=false)
    @OnDelete(action = OnDeleteAction.NO_ACTION)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Region region;

    @ManyToOne(fetch = FetchType.LAZY, optional=true)
    @JoinColumn(name="province_id", nullable=true)
    @OnDelete(action = OnDeleteAction.NO_ACTION)
    @JsonIgnore
    private Province province;

    @ManyToOne(fetch = FetchType.LAZY, optional=true)
    @JoinColumn(name = "district_id", nullable=true)
    @OnDelete(action = OnDeleteAction.NO_ACTION)
    @JsonIgnore
    private District district;

    @ManyToOne(fetch = FetchType.LAZY, optional=true)
    @JoinColumn(name="sub_district_id", nullable=true)
    @OnDelete(action = OnDeleteAction.NO_ACTION)
    @JsonIgnore
    private SubDistrict sub_district;

//    @ManyToOne(fetch = FetchType.LAZY, optional=false)
//    @JoinColumn(name="statusId", nullable=false)
//    @OnDelete(action = OnDeleteAction.NO_ACTION)
//    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
//    private Status status;

    private StatusCodeEnum status;

    @Column(unique=false, nullable=true)
    private String site_key;

    @Column(columnDefinition="boolean default false")
    private Boolean keyGenerating = false;

    private LocalDateTime lastKeyGenerate;
    private LocalDateTime lastKeyUpdate;

    @Column(unique=true, nullable=false)
    private String code;

    @NotEmpty(message = "Please provide name_th")
    private String name_th;

    @Column(columnDefinition="boolean default false")
    private Boolean isInstall = false;

    private LocalDateTime installedDate;
    private LocalDateTime reportStatusAt;

    @Column(columnDefinition="boolean default true")
    private Boolean active = true;

    private Boolean emerg_key_success = false;

    private String name_en;
    private String lat;
    private String lon;
    private String ip_address;
    private int port;
    private String subnet_mask;
    private String default_gateway;
    private String contact_point_name;
    private String contact_point_tel;
    private String address;
    private Double temperature;
    private String version;
    private Integer motion;
    private Integer firmware_option;

    @Transient
    private MultipartFile image1;

    @OneToMany(
            mappedBy = "site",
            cascade = CascadeType.ALL,
            fetch = FetchType.LAZY,
            orphanRemoval = true
    )
    @Fetch(FetchMode.SELECT)
    private List<Cabinet> cabinets = new ArrayList<>();

    public Site() {
    }

    // for create site from csv
    public Site(String code, String name_th, Region region,  Province province, District district,
                SubDistrict sub_district, StatusCodeEnum status) {
        this.code = code;
        this.name_th = name_th;
        this.name_en = "[EN] " + name_th;
        this.region = region;
        this.province = province;
        this.district = district;
        this.sub_district = sub_district;
        this.status = status;
        this.active = true;
    }

    // for create mock
    public Site(String code, String name_th, Region region, Province province, District district,
                SubDistrict sub_district, LocalDateTime installedDate, String lat, String lon, String ip_address, int port,
                String subnet_mask, String default_gateway, String address, StatusCodeEnum status, boolean isInstall) {
        this.code = code;
        this.name_th = name_th;
        this.name_en = "[EN] " + name_th;
        this.region = region;
        this.province = province;
        this.district = district;
        this.sub_district = sub_district;
        this.installedDate = installedDate;
        this.lat = lat;
        this.lon = lon;
        this.ip_address = ip_address;
        this.port = port;
        this.subnet_mask = subnet_mask;
        this.default_gateway = default_gateway;
        this.address = address;
        this.active = true;
        this.status = status;
        this.isInstall = isInstall;
        this.keyGenerating = true;
    }

    public Site(String name_th) {
    }

    public void addCabinet(Cabinet cabinet) {
        cabinets.add(cabinet);
        cabinet.setSite(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Site choice = (Site) o;
        return Objects.equals(id, choice.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
