package dev.niceone.smartsiteappserver.payload;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class CabinetCsvRequest {
    private String is_deleted;
    private String is_enabled;
    private String cabinet_code;
    private String site_code;
    private String updated_dt;
    private String is_installed;
    private String mac_address;
    private String device_name;
}
