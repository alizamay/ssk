package dev.niceone.smartsiteappserver.payload.response;

import dev.niceone.smartsiteappserver.model.LogEntry;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
public class AlarmResp {
    public List<LogEntry> data;
    public String pageState;
    public boolean hasMorePages;
}

