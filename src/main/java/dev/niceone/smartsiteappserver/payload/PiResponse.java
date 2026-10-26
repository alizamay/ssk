package dev.niceone.smartsiteappserver.payload;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class PiResponse {
    private Long id;
    private String emr_key;
    private Long status_id;
    private String status_name;
    private String status_code;
    private Boolean active;
}
