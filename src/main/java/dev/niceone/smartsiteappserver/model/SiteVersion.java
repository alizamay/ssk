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
@Getter @Setter
@Table(name = "site_version")
public class SiteVersion extends AuditModel implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator="site_version_seq_gen")
    @SequenceGenerator(name="site_version_seq_gen", sequenceName="site_version_seq", allocationSize=1)
    private Long id;

    @Column(columnDefinition="boolean default true")
    private Boolean active;

    private Long siteId;
    private String versionCode;
    private String versionName;
    public SiteVersion() {
    }

    public SiteVersion(Boolean active,Long siteId,String  versionCode,String versionName) {
        this.siteId = siteId;
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.active = active;
    }
}