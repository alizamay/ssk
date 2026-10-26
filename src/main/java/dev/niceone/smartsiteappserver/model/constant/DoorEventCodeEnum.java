package dev.niceone.smartsiteappserver.model.constant;

import lombok.Getter;

@Getter
public enum DoorEventCodeEnum {
    DOOR_EVENT_OPEN("DOOR_EVENT_OPEN", "OPEN", 0),
    DOOR_EVENT_CLOSE("DOOR_EVENT_CLOSE", "CLOSE", 1),
    DOOR_EVENT_UNKNOW("DOOR_EVENT_UNKNOW", "UNKNOW", 2);

    public final String code;
    public final String name;
    public final int id;

    DoorEventCodeEnum(String code, String name, int id) {
        this.code = code;
        this.name = name;
        this.id = id;
    }

    public static DoorEventCodeEnum getByName(String name) {
        for(DoorEventCodeEnum e : values()) {
            if(e.name.equals(name)) return e;
        }
        return DOOR_EVENT_UNKNOW;
    }
    public static DoorEventCodeEnum getById(Integer id) {
        for(DoorEventCodeEnum e : values()) {
            if(e.id == id) return e;
        }
        return DOOR_EVENT_UNKNOW;
    }
}