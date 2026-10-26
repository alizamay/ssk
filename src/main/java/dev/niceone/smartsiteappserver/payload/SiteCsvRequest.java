package dev.niceone.smartsiteappserver.payload;

import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@ToString
public class SiteCsvRequest {
    private String is_deleted;
    private String is_enabled;
    private String site_code;
    private String site_name;
    private String region_code;
    private String lat;
    private String lon;
    private String ipaddr;
    private int port;
    private String contact_point_name;
    private String contact_point_tel;
    private String address;
    private String province;
    private String district;
    private String sub_district;
    private String subnet_mask;
    private String default_gateway;
    private String installed_dt;
    private String is_installed;
    private String prov_id;
    private String amp_id;
    private String dist_id;
    private String emerg_key_success;
    private String changed_dt;
}
