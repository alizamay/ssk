package dev.niceone.smartsiteappserver.payload.request;

import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class PiAlarmLogRequest {
    private String site_code;
    private String cabinet_code;
    private int alarm_id;
    private int status;
}
