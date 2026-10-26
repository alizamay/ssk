package dev.niceone.smartsiteappserver.payload.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class LogUpdateAlarm {
    private int alarm_type;
    private String site_code;
    private LocalDateTime resolve_date;

}
