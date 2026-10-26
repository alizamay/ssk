package dev.niceone.smartsiteappserver.model.constant;

import lombok.Getter;

@Getter
public enum DeviceTypeCodeEnum {
    DEVICE_TYPE_INITIALIZE("DEVICE_TYPE_INITIALIZE", "Initialize", 0, false),
    DEVICE_TYPE_SMART_LOCK("DEVICE_TYPE_SMART_LOCK", "Smart Lock", 1, true),
    DEVICE_TYPE_BUZZER("DEVICE_TYPE_BUZZER", "Buzzer", 2, true),
    DEVICE_TYPE_MIPI_CAMERA("DEVICE_TYPE_MIPI_CAMERA", "MIPI Camera", 3, true),
    DEVICE_TYPE_IP_CAMERA("DEVICE_TYPE_IP_CAMERA", "IP Camera", 4, true),
    DEVICE_TYPE_MOTION_SENSOR("DEVICE_TYPE_MOTION_SENSOR", "Motion Sensor", 5, true),
    DEVICE_TYPE_VIBRATION_SENSOR("DEVICE_TYPE_VIBRATION_SENSOR", "Vibration Sensor", 6, true),
    DEVICE_TYPE_DOOR_SWITCH("DEVICE_TYPE_DOOR_SWITCH", "Door Switch", 7, true),
    DEVICE_TYPE_SD_CARD("DEVICE_TYPE_SD_CARD", "SD Card", 8, true),
    DEVICE_TYPE_SLAVE_CONTROLLER("DEVICE_TYPE_SLAVE_CONTROLLER", "Slave Controller", 9, true),
    DEVICE_TYPE_KEY_SWITCH("DEVICE_TYPE_KEY_SWITCH", "Key Switch", 10, true),
;
    public final String code;
    public final String name;
    public final int id;
    public final boolean active;

    DeviceTypeCodeEnum(String code, String name, int id, boolean active) {
        this.code = code;
        this.name = name;
        this.id = id;
        this.active = active;
    }

    public static DeviceTypeCodeEnum getById(int id) {
        for(DeviceTypeCodeEnum e : values()) {
            if(e.id == id) return e;
        }
        return null;
    }

    public static DeviceTypeCodeEnum getByCode(String code) {
        for(DeviceTypeCodeEnum e : values()) {
            if(e.code.equals(code)) return e;
        }
        return null;
    }

    public static DeviceTypeCodeEnum getByName(String name) {
        for(DeviceTypeCodeEnum e : values()) {
            if(e.name.equals(name)) return e;
        }
        return null;
    }
}

