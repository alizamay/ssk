package dev.niceone.smartsiteappserver.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class OnlineOfflineSummaryDaily {
    private LocalDate date;
    private long onlineCount;
    private long offlineCount;
}
