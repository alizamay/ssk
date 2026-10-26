package dev.niceone.smartsiteappserver.model.constant;

import lombok.Getter;

@Getter
public enum EventTypeCodeEnum {
    EVENT_TYPE_NO_EVENT("EVENT_TYPE", "NO_EVENT", 0),
    EVENT_TYPE_SMART_LOCK_LOCKED("EVENT_TYPE_SMART_LOCK_LOCKED", "SMART_LOCK_LOCKED", 1),
    EVENT_TYPE_SMART_LOCK_UNLOCKED("EVENT_TYPE_SMART_LOCK_UNLOCKED", "SMART_LOCK_UNLOCKED", 2),
    EVENT_TYPE_MOTION_DETECTED("EVENT_TYPE_MOTION_DETECTED", "MOTION_DETECTED", 3),
    EVENT_TYPE_VIBRATION_DETECTED("EVENT_TYPE_VIBRATION_DETECTED", "VIBRATION_DETECTED", 4),
    EVENT_TYPE_UNKNOWN_EVENT("EVENT_TYPE_UNKNOWN_EVENT", "UNKNOWN_EVENT", 5),
    EVENT_TYPE_DOOR_SWITCH_CLOSED("EVENT_TYPE_DOOR_SWITCH_CLOSED", "DOOR_SWITCH_CLOSED", 6),
    EVENT_TYPE_DOOR_SWITCH_OPENED("EVENT_TYPE_DOOR_SWITCH_OPENED", "DOOR_SWITCH_OPENED", 7),
    EVENT_TYPE_SD_CARD_FULL("EVENT_TYPE_SD_CARD_FULL", "SD_CARD_FULL", 8),
    EVENT_TYPE_SLAVE_CONTROLLER_DOWN("EVENT_TYPE_SLAVE_CONTROLLER_DOWN", "SLAVE_CONTROLLER_DOWN", 9),
    EVENT_TYPE_KEY_SWITCH_LOCKED("EVENT_TYPE_KEY_SWITCH_LOCKED", "KEY_SWITCH_LOCKED", 10),
    EVENT_TYPE_KEY_SWITCH_UNLOCKED("EVENT_TYPE_KEY_SWITCH_UNLOCKED", "KEY_SWITCH_UNLOCKED", 11);

    public final String code;
    public final String name;
    public final int id;

    EventTypeCodeEnum(String code, String name, int id) {
        this.code = code;
        this.name = name;
        this.id = id;
    }

    public static EventTypeCodeEnum getByName(String name) {
        for(EventTypeCodeEnum e : values()) {
            if(e.name.equals(name)) return e;
        }
        return EVENT_TYPE_NO_EVENT;
    }

    public static EventTypeCodeEnum getById(Integer id) {
        for(EventTypeCodeEnum e : values()) {
            if(e.id == id) return e;
        }
        return EVENT_TYPE_NO_EVENT;
    }
}