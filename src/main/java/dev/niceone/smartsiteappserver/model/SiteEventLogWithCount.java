package dev.niceone.smartsiteappserver.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Setter
@Getter
public class SiteEventLogWithCount {
    private UUID id;
    private LocalDateTime createDate;
    private Boolean active;
    private Integer alarmType;
    private String cabinetId;
    private String deviceId;
    private LocalDateTime resolveDate;
    private String siteId;
    private String siteCode;
    private String siteNameTh;
    private String siteNameEn;
    private LocalDateTime updateDate;
    private Boolean writeToFile;
    private String alarmTypeCode;
    private String alarmTypeName;
    private String cabinetNo;
    private String cabinetCode;
    private Integer lockEventId;
    private String lockEventName;
    private Integer doorEventId;
    private String doorEventName;
    private String statusCode;
    private String regionCode;
    private String regionName;
    private Boolean isResolve;
    private Integer countCreateEvent;
    private Integer countResolveEvent;
}
