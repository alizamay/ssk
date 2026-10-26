package dev.niceone.smartsiteappserver.mapper;

import dev.niceone.smartsiteappserver.model.*;
import dev.niceone.smartsiteappserver.model.constant.DoorEventCodeEnum;
import dev.niceone.smartsiteappserver.model.constant.EventTypeCodeEnum;
import dev.niceone.smartsiteappserver.model.constant.LockEventCodeEnum;
import dev.niceone.smartsiteappserver.payload.AlarmLogResponse;
import dev.niceone.smartsiteappserver.payload.response.LogModel;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class AlarmLogModelMapper {
    public static AlarmLogResponse mapAlarmLogResponse(AlarmLog alarmLog) {
        AlarmLogResponse alarmLogResponse = new AlarmLogResponse();
        // AlarmLog
        alarmLogResponse.setId(alarmLog.getId());
        alarmLogResponse.setCreatedAt(alarmLog.getCreatedAt());
        alarmLogResponse.setResolveDate(alarmLog.getResolveDate());
        alarmLogResponse.setActive(alarmLog.getActive());

        // AlarmType
        alarmLogResponse.setAlarmTypeCode(alarmLog.getAlarmType().code);
        alarmLogResponse.setAlarmTypeName(alarmLog.getAlarmType().name);

//        Device device = alarmLog.getDevice();
        Cabinet cabinet = alarmLog.getCabinet();
//        Site site = alarmLog.getSite();
//        if (alarmLog.getDevice() != null) {
//            device = alarmLog.getDevice();
//            cabinet = device.getCabinet();
//            site = cabinet.getSite();
//        }
//        else if (alarmLog.getCabinet() != null) {
//            site = cabinet.getSite();
//        }

        // Device
//        if (device != null) {
//            alarmLogResponse.setDeviceTypeCode(device.getDeviceTypeCode().code);
//            alarmLogResponse.setDeviceTypeName(device.getDeviceTypeCode().name);
//            alarmLogResponse.setStatusCode(device.getStatus().code);
//            alarmLogResponse.setStatusName(device.getStatus().getName());
//            alarmLogResponse.setEventTypeCodeEnum(device.getEventType());
//        }
        // Cabinet
        if (cabinet != null) {
            alarmLogResponse.setCabinetNo(cabinet.getCabinetNo());
            alarmLogResponse.setCabinetCode(cabinet.getCode());
            alarmLogResponse.setLockEventCodeEnum(cabinet.getLockEventCode());
            alarmLogResponse.setLockEventName(cabinet.getLockEventCode().name);
//            alarmLogResponse.setLockEventTypeCodeEnum(cabinet.getLockEventType());
            alarmLogResponse.setDoorEventCodeEnum(cabinet.getDoorEventCode());
            alarmLogResponse.setDoorEventName(cabinet.getDoorEventCode().name);
//            alarmLogResponse.setDoorEventTypeCodeEnum(cabinet.getDoorEventType());
        }
        // Site
        alarmLogResponse.setSiteCode(alarmLog.getSite().getCode());
        alarmLogResponse.setSiteNameTh(alarmLog.getSite().getName_th());
        alarmLogResponse.setSiteNameEn(alarmLog.getSite().getName_en());
        alarmLogResponse.setStatusCode(alarmLog.getSite().getStatus().code);
        // Region
        Region region = alarmLog.getSite().getRegion();
        alarmLogResponse.setRegionCode(region.getCode());
        alarmLogResponse.setRegionName(region.getName());
        return alarmLogResponse;
    }


    public static AlarmLogResponse mapLogEntryResponse(LogEntry logEntry) {
        AlarmLogResponse alarmLogResponse = new AlarmLogResponse();
        long randomPositiveLong = Math.abs(logEntry.getId().getLeastSignificantBits());
        // AlarmLog
        alarmLogResponse.setId(randomPositiveLong);


        LocalDateTime createTimeUTC7 = logEntry.getCreate_date().plusHours(7);
        alarmLogResponse.setCreatedAt(createTimeUTC7);

        LocalDateTime reolveTimeUTC7 = logEntry.getResolve_date().plusHours(7);
        alarmLogResponse.setResolveDate(reolveTimeUTC7);
        alarmLogResponse.setActive(logEntry.isActive());

        // AlarmType
        alarmLogResponse.setAlarmTypeCode(logEntry.getAlarm_type_code());
        alarmLogResponse.setAlarmTypeName(logEntry.getAlarm_type_name());

        int cabinetNo =  0;
        try { cabinetNo = Integer.parseInt(logEntry.getCabinet_no());} catch (NumberFormatException e) {}

        alarmLogResponse.setCabinetNo(cabinetNo);
        alarmLogResponse.setCabinetCode(logEntry.getCabinet_code());
        alarmLogResponse.setLockEventCodeEnum(LockEventCodeEnum.getByName(logEntry.getLock_event_name()));
        alarmLogResponse.setLockEventName(logEntry.getLock_event_name());

        alarmLogResponse.setDoorEventCodeEnum(DoorEventCodeEnum.getByName(logEntry.getDoor_event_name()));
        alarmLogResponse.setDoorEventName(logEntry.getDoor_event_name());
        // Site
        alarmLogResponse.setSiteCode(logEntry.getSite_code());
        alarmLogResponse.setSiteNameTh(logEntry.getSite_name_th());
        alarmLogResponse.setSiteNameEn(logEntry.getSite_name_en());
        alarmLogResponse.setStatusCode(logEntry.getStatus_code());
        // Region
        alarmLogResponse.setRegionCode(logEntry.getRegion_code());
        alarmLogResponse.setRegionName(logEntry.getRegion_name());
        return alarmLogResponse;
    }


    public static AlarmLogResponse mapLogEntryResponse(LogModel logEntry) {
        AlarmLogResponse alarmLogResponse = new AlarmLogResponse();
        long randomPositiveLong = Math.abs(logEntry.getId().getLeastSignificantBits());
        // AlarmLog
        alarmLogResponse.setId(randomPositiveLong);

        String receivedDateTime = "2019-11-13T00:11:08+05:00";

        OffsetDateTime parsedCDate = OffsetDateTime.parse(logEntry.getCreate_date());
        ZonedDateTime createDate = parsedCDate.atZoneSameInstant(ZoneId.systemDefault());

        LocalDateTime createTimeUTC7 = createDate.toLocalDateTime();
        alarmLogResponse.setCreatedAt(createTimeUTC7);

        OffsetDateTime parsedRDate = OffsetDateTime.parse(logEntry.getResolve_date());
        ZonedDateTime resolveDate = parsedRDate.atZoneSameInstant(ZoneId.systemDefault());

        LocalDateTime reolveTimeUTC7 = resolveDate.toLocalDateTime();
        alarmLogResponse.setResolveDate(reolveTimeUTC7);
        alarmLogResponse.setActive(logEntry.isActive());

        // AlarmType
        alarmLogResponse.setAlarmTypeCode(logEntry.getAlarm_type_code());
        alarmLogResponse.setAlarmTypeName(logEntry.getAlarm_type_name());

        int cabinetNo =  0;
        try { cabinetNo = Integer.parseInt(logEntry.getCabinet_no());} catch (NumberFormatException e) {}

        alarmLogResponse.setCabinetNo(cabinetNo);
        alarmLogResponse.setCabinetCode(logEntry.getCabinet_code());
        alarmLogResponse.setLockEventCodeEnum(LockEventCodeEnum.getByName(logEntry.getLock_event_name()));
        alarmLogResponse.setLockEventName(logEntry.getLock_event_name());

        alarmLogResponse.setDoorEventCodeEnum(DoorEventCodeEnum.getByName(logEntry.getDoor_event_name()));
        alarmLogResponse.setDoorEventName(logEntry.getDoor_event_name());
        // Site
        alarmLogResponse.setSiteCode(logEntry.getSite_code());
        alarmLogResponse.setSiteNameTh(logEntry.getSite_name_th());
        alarmLogResponse.setSiteNameEn(logEntry.getSite_name_en());
        alarmLogResponse.setStatusCode(logEntry.getStatus_code());
        // Region
        alarmLogResponse.setRegionCode(logEntry.getRegion_code());
        alarmLogResponse.setRegionName(logEntry.getRegion_name());
        alarmLogResponse.setCount(logEntry.getCount());
        alarmLogResponse.setTimestamps(logEntry.getTimestamps());
        return alarmLogResponse;
    }

    public static  AlarmLogResponse mapToAlarmLogResponse(SiteEventLog log) {
        AlarmLogResponse dto = new AlarmLogResponse();
        dto.setId(log.getId().getMostSignificantBits() & Long.MAX_VALUE); // หรือใช้ custom UUID mapping
        dto.setCreatedAt(log.getCreateDate());
        dto.setResolveDate(log.getResolveDate());
        dto.setActive(log.getActive());
        dto.setAlarmTypeCode(log.getAlarmTypeCode());
        dto.setAlarmTypeName(log.getAlarmTypeName());

        dto.setStatusCode(log.getStatusCode());
        dto.setSiteCode(log.getSiteCode());
        dto.setSiteNameTh(log.getSiteNameTh());
        dto.setSiteNameEn(log.getSiteNameEn());

        dto.setRegionCode(log.getRegionCode());
        dto.setRegionName(log.getRegionName());

        if (log.getCabinetNo() != null) {
            try {
                dto.setCabinetNo(Integer.parseInt(log.getCabinetNo()));
            } catch (Exception e) {
                dto.setCabinetNo(0);
            }
        }

        dto.setCabinetCode(log.getCabinetCode());
        dto.setLockEventName(log.getLockEventName());
        dto.setDoorEventName(log.getDoorEventName());

        // enums ถ้าคุณใช้ Enum.valueOf(...) ต้องระวัง null
        if (log.getLockEventId() != null)
            dto.setLockEventCodeEnum(LockEventCodeEnum.getById(log.getLockEventId()));
        if (log.getDoorEventId() != null)
            dto.setDoorEventCodeEnum(DoorEventCodeEnum.getById(log.getDoorEventId()));

        // สามารถเพิ่ม mapping อื่น ๆ ได้ตามต้องการ
        dto.setCount(0);
        dto.setTimestamps("");
        dto.setLogId(log.getId());
        return dto;
    }


}
