package dev.niceone.smartsiteappserver.model.constant;

import lombok.Getter;

@Getter
public enum StatusCodeEnum {
    STATUS_DOWN("STATUS_DOWN", "Down", 0),
    STATUS_UP("STATUS_UP", "Up", 1),
    STATUS_UNKNOWN("STATUS_UNKNOWN", "Unknown", 2);

    public final String code;
    public final String name;
    public final int id;

    StatusCodeEnum(String code, String name, int id) {
        this.code = code;
        this.name = name;
        this.id = id;
    }

    public static StatusCodeEnum getByName(String name) {
        for(StatusCodeEnum e : values()) {
            if(e.name.equals(name)) return e;
        }
        return STATUS_UNKNOWN;
    }

    public static StatusCodeEnum getById(Integer id) {
        for(StatusCodeEnum e : values()) {
            if(e.id == id) return e;
        }
        return STATUS_UNKNOWN;
    }
}
