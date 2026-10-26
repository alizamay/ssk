package dev.niceone.smartsiteappserver.payload.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
@Getter
@Setter
public class LogUpdateRequest {
    private List<String> ids;
    private String id;
    private LocalDateTime resolve_date;

}


