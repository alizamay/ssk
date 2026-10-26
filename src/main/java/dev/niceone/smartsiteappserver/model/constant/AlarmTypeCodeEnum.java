package dev.niceone.smartsiteappserver.model.constant;

import lombok.Getter;

@Getter
public enum AlarmTypeCodeEnum {
    ALARM_TYPE_LOCK_FAILED(1, "A001", "Smart Lock Failure", "critical", true,
            "Smart Lock Failed", "Smart_Lock_Failed"),
    ALARM_TYPE_DOORSW_FAILED(2, "A002", "Door Switch Failure", "critical", true,
            "Door Switch Failed", "Door_Switch_Failed"),
    ALARM_TYPE_PINCAM_FAILED(3, "A003", "Pinhole Camera Failure", "major", true,
            "Pinhole Camera Failed", "Pinhole_Camera_Failed"),
    ALARM_TYPE_CCTV_FAILED(4, "A004", "IP Camera Failure", "major", true,
            "IP Camera Failed", "IP_Camera_Failed"),
    ALARM_TYPE_VIBRAT_FAILED(5, "A005", "Vibration Failure", "minor", true,
            "Vibration Sensor Failed", "Vibration_Sensor_Failed"),
    ALARM_TYPE_MOTION_FAILED(6, "A006", "Motion Failure", "minor", true,
            "Motion Sensor Failed", "Motion_Sensor_Failed"),
    ALARM_TYPE_BUZZER_FAILED(7, "A007", "Buzzer Failure", "minor", true,
            "Buzzer Failure", "Buzzer_Failure"),
    ALARM_TYPE_SDCARD_FAILED(8, "A008", "SD Card Failure", "minor", true,
            "SD Card Failure", "SD_Card_Failure"),
    ALARM_TYPE_UNAUTH_OPENED(9, "A009", "Unauthorized opened", "critical", true,
            "Unauthorized Open", "Unauthorized_Open"),
    ALARM_TYPE_VIBRAT_EXCESS(10, "A010", "Exceeding Vibration Detected", "minor", true,
            "Vibration Detected", "Vibration_Detected"),
    ALARM_TYPE_MOTION_EXCESS(11, "A011", "Exceeding Motion detected", "minor", true,
            "Motion Detected", "Motion_Detected"),
    ALARM_TYPE_CAM_LOST(12, "A012", "Camera - Lost", "minor", true,
            "Camera - Lost", "Camera - Lost"),
    ALARM_TYPE_LOCK_ATTEMPT_FAILED(13, "A013", "Failed to Lock (Attempted)", "minor", true,
            "Failed to Lock (Attempted)", "Failed to Lock (Attempted)"),
    ALARM_TYPE_SDCARD_FULL(14, "A014", "SD Card Full", "major", true,
            "SD Card Full", "SD_Card_Full"),
    ALARM_TYPE_COMM_LOST_SERV(15, "A015", "Communication Lost ( Server detects)", "major", true,
            "Communication Lost", "Communication_Lost"),
    ALARM_TYPE_COMM_LOST_PI(16, "A016", "Communication Lost ( Controller detects)", "major", true,
            "Communication Lost", "Communication_Lost"),
    ALARM_TYPE_LINK_DOWN(17, "A017", "Fiber optics down", "major", true,
            "Fiber optics down", "Fiber optics down"),
    ALARM_TYPE_SLAVE_DOWN(18, "A018", "Slave down", "major", true,
            "Slave Down", "Slave_Down"),
    ALARM_TYPE_NO_IMG_UPLOADED(19, "A019", "No image uploaded", "minor", true,
            "No Image Uploaded", "No_Image_Uploaded"),
    ALARM_TYPE_AUX1(20, "A020", "AUX1_Alarm", "minor", true, "AUX1_Alarm", "AUX1_Alarm"),
    ALARM_TYPE_AUX2(21, "A021", "AUX2_Alarm", "minor", true, "AUX2_Alarm", "AUX2_Alarm"),
    ALARM_TYPE_AUX3(22, "A022", "AUX3_Alarm", "minor", true, "AUX3_Alarm", "AUX3_Alarm");

    public final int id;
    public final String code;
    public final String name;
    public final String severity;
    public final boolean active;
    public final String aKey;
    public final String aName;

    AlarmTypeCodeEnum(int id, String code, String name, String severity, boolean active, String aKey, String aName) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.severity = severity;
        this.active = active;
        this.aKey = aKey;
        this.aName = aName;
    }

    public static AlarmTypeCodeEnum getById(int id) {
        for(AlarmTypeCodeEnum e : values()) {
            if(e.id == id) return e;
        }
        return null;
    }

    public static AlarmTypeCodeEnum getByCode(String code) {
        for(AlarmTypeCodeEnum e : values()) {
            if(e.code.equals(code)) return e;
        }
        return null;
    }
}