package dev.niceone.smartsiteappserver.payload;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class AlarmLogRequest {
    private int alarm_type_id;
    private Long device_id;
}
