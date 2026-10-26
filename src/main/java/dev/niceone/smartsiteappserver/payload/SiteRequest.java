package dev.niceone.smartsiteappserver.payload;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.niceone.smartsiteappserver.model.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.http.ResponseEntity;

import javax.persistence.*;
import javax.validation.constraints.NotEmpty;
import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

@Getter
@ToString
public class SiteRequest {
    private Long id;
    private String code;
    private String name_th;
    private String name_en;
    private Long region_id;
    private Long province_id;
    private Long district_id;
    private Long sub_district_id;
    private String address;
    private String lat;
    private String lon;
    private String ip_address;
    private int port;
    private String subnet_mask;
    private String default_gateway;
    private LocalDateTime installed_date;
    private Boolean isInstall;
    private LocalDateTime changed_date;
    private Long status_id;
    private File image1;
    private List<CabinetRequest> cabinets;

    // Todo: set site_key
    // Todo: check changed_date
}
