package dev.niceone.smartsiteappserver.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
public class LogDataModel {
    private UUID id;
    private boolean active;
    private int alarm_type;
    private String cabinet_id;
    private String device_id;
    private LocalDateTime resolve_date;
    private String site_id;
    private LocalDateTime create_date;
    private boolean write_to_file;

    private String alarm_type_code;
    private String alarm_type_name;
    private String cabinet_no;
    private String cabinet_code;
    private int lock_event_id;
    private String lock_event_name;
    private int door_event_id;
    private String door_event_name;
    private String site_code;
    private String site_name_th;
    private String site_name_en;
    private String status_code;
    private String region_code;
    private String region_name;
    private LocalDateTime update_date; // New field
    // Getters and Setters for all fields
//    public UUID getId() {
//        return id;
//    }
//
//    public void setId(UUID id) {
//        this.id = id;
//    }
//
//    public boolean isActive() {
//        return active;
//    }
//
//    public void setActive(boolean active) {
//        this.active = active;
//    }
//
//    public int getAlarmType() {
//        return alarm_type;
//    }
//
//    public void setAlarmType(int alarmType) {
//        this.alarm_type = alarmType;
//    }
//
//    public String getCabinetId() {
//        return cabinet_id;
//    }
//
//    public void setCabinetId(String cabinetId) {
//        this.cabinet_id = cabinetId;
//    }
//
//    public String getDeviceId() {
//        return device_id;
//    }
//
//    public void setDeviceId(String deviceId) {
//        this.device_id = deviceId;
//    }
//
//    public LocalDateTime getResolveDate() {
//        return resolve_date;
//    }
//
//    public void setResolveDate(LocalDateTime resolveDate) {
//        this.resolve_date = resolveDate;
//    }
//
//    public String getSiteId() {
//        return site_id;
//    }
//
//    public void setSiteId(String siteId) {
//        this.site_id = siteId;
//    }
//
//    public LocalDateTime getCreateDate() {
//        return create_date;
//    }
//
//    public void setCreateDate(LocalDateTime createDate) {
//        this.create_date = createDate;
//    }
//
//    public boolean isWriteToFile() {
//        return write_to_file;
//    }
//
//    public void setWriteToFile(boolean writeToFile) {
//        this.write_to_file = writeToFile;
//    }
//
//    public String getAlarmTypeCode() {
//        return alarm_type_code;
//    }
//
//    public void setAlarmTypeCode(String alarmTypeCode) {
//        this.alarm_type_code = alarmTypeCode;
//    }
//
//    public String getAlarmTypeName() {
//        return alarm_type_name;
//    }
//
//    public void setAlarmTypeName(String alarmTypeName) {
//        this.alarm_type_name = alarmTypeName;
//    }
//
//    public String getCabinetNo() {
//        return cabinet_no;
//    }
//
//    public void setCabinetNo(String cabinetNo) {
//        this.cabinet_no = cabinetNo;
//    }
//
//    public String getCabinetCode() {
//        return cabinet_code;
//    }
//
//    public void setCabinetCode(String cabinetCode) {
//        this.cabinet_code = cabinetCode;
//    }
//
//    public int getLockEventId() {
//        return lock_event_id;
//    }
//
//    public void setLockEventId(int lockEventId) {
//        this.lock_event_id = lockEventId;
//    }
//
//    public String getLockEventName() {
//        return lock_event_name;
//    }
//
//    public void setLockEventName(String lockEventName) {
//        this.lock_event_name = lockEventName;
//    }
//
//    public int getDoorEventId() {
//        return door_event_id;
//    }
//
//    public void setDoorEventId(int doorEventId) {
//        this.door_event_id = doorEventId;
//    }
//
//    public String getDoorEventName() {
//        return door_event_name;
//    }
//
//    public void setDoorEventName(String doorEventName) {
//        this.door_event_name = doorEventName;
//    }
//
//    public String getSiteCode() {
//        return site_code;
//    }
//
//    public void setSiteCode(String siteCode) {
//        this.site_code = siteCode;
//    }
//
//    public String getSiteNameTh() {
//        return site_name_th;
//    }
//
//    public void setSiteNameTh(String siteNameTh) {
//        this.site_name_th = siteNameTh;
//    }
//
//    public String getSiteNameEn() {
//        return site_name_en;
//    }
//
//    public void setSiteNameEn(String siteNameEn) {
//        this.site_name_en = siteNameEn;
//    }
//
//    public String getStatusCode() {
//        return status_code;
//    }
//
//    public void setStatusCode(String statusCode) {
//        this.status_code = statusCode;
//    }
//
//    public String getRegionCode() {
//        return region_code;
//    }
//
//    public void setRegionCode(String regionCode) {
//        this.region_code = regionCode;
//    }
//
//    public String getRegionName() {
//        return region_name;
//    }
//
//    public void setRegionName(String regionName) {
//        this.region_name = regionName;
//    }
//
//    public LocalDateTime getUpdateDate() {
//        return update_date;
//    }
//
//    public void setUpdateDate(LocalDateTime updateDate) {
//        this.update_date = updateDate;
//    }
}