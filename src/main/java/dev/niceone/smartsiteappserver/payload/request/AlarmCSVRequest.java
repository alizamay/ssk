package dev.niceone.smartsiteappserver.payload.request;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class AlarmCSVRequest {
    private String start_created_at;
    private String end_created_at;
    private String keyword;
}
