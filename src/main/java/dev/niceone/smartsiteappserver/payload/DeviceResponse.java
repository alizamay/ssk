package dev.niceone.smartsiteappserver.payload;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@Setter
@ToString
public class DeviceResponse {
    private Long id;
    private LocalDateTime installed_date;
    private Boolean is_install;
    private Boolean is_enable;
    private Boolean active;

    // DeviceType
    private int type_id;
    private String type_code;
    private String type_name;

    private StatusResponse status;
}

