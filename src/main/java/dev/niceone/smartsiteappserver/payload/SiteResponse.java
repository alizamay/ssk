package dev.niceone.smartsiteappserver.payload;

import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Setter
@Getter
public class SiteResponse {
    private Long id;
    private String code;
    private String name_th;
    private String name_en;

    private StatusResponse status;

    private Long region_id;
    private String region_name;
    private String region_code;

    private Long province_id;
    private String province_name_th;
    private String province_name_en;
    private String province_code;

    private Long district_id;
    private String district_name_th;
    private String district_name_en;
    private Integer district_zipcode;

    private Long sub_district_id;
    private String sub_district_name_th;
    private String sub_district_name_en;

    private String site_key;

    private LocalDateTime installed_date;
    private Boolean isInstall;
    private LocalDateTime updatedAt;

    private Boolean active;
    private String lat;
    private String lon;
    private String ip_address;
    private int port;
    private String subnet_mask;
    private String default_gateway;
    private String address;
    private MultipartFile image1;

    private int cabinet_count;
    private List<CabinetResponse> cabinets;
    private String version;
    private String version_number;
    private Double temperature;
    private Integer motion;
    private Integer firmware_option;
}
