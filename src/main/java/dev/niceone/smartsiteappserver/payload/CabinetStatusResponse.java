package dev.niceone.smartsiteappserver.payload;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
public class CabinetStatusResponse {
    private String site_code;
    private String cabinet_code;
    private String locked;
    private String is_emergency;
    private LocalDateTime last_updated;
    private String emerg_key;
    private String mac_address;
    private String device_name;
}
