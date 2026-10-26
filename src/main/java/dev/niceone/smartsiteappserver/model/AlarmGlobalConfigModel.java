package dev.niceone.smartsiteappserver.model;

import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;

@Getter
@Setter
public class AlarmGlobalConfigModel {
    private Long id;
    private Boolean is_disabled;
    private Integer daily_limit;
    private String updated_at;
}
