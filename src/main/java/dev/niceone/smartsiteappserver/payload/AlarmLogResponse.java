package dev.niceone.smartsiteappserver.payload;

import dev.niceone.smartsiteappserver.model.constant.DoorEventCodeEnum;
import dev.niceone.smartsiteappserver.model.constant.EventTypeCodeEnum;
import dev.niceone.smartsiteappserver.model.constant.LockEventCodeEnum;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter
public class AlarmLogResponse {
    // AlarmLog
    private Long id;
    private LocalDateTime createdAt;
    private LocalDateTime resolveDate;
    private Boolean active;

    // AlarmType
    private String alarmTypeCode;
    private String alarmTypeName;

    // Device
    private String deviceTypeCode;
    private String deviceTypeName;
    private String statusCode;
    private String statusName;
    private EventTypeCodeEnum eventTypeCodeEnum;

    // Cabinet
    private int cabinetNo;
    private String cabinetCode;
    private LockEventCodeEnum lockEventCodeEnum;
    private String lockEventName;
    private EventTypeCodeEnum lockEventTypeCodeEnum;
    private DoorEventCodeEnum doorEventCodeEnum;
    private String doorEventName;
    private EventTypeCodeEnum doorEventTypeCodeEnum;

    // Site
    private String siteCode;
    private String siteNameTh;
    private String siteNameEn;

    // Region
    private String regionCode;
    private String regionName;

    private String pageState;

    private Integer count;
    private Integer resolve_count;
    private String timestamps;
    private UUID logId;
}
