package dev.niceone.smartsiteappserver.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuthinConfigModel {
    private Long id;
    private String site_code;
    private Boolean is_disabled;
    private Integer daily_limit;
    private String updated_at;

}
