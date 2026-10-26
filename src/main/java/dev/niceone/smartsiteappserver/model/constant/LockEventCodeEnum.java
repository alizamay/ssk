package dev.niceone.smartsiteappserver.model.constant;

import lombok.Getter;

@Getter
public enum LockEventCodeEnum {
    LOCK_EVENT_LOCK("LOCK_EVENT_LOCK", "LOCK", 0),
    LOCK_EVENT_UNLOCK("LOCK_EVENT_UNLOCK", "UNLOCK", 1),
    LOCK_EVENT_UNKNOW("LOCK_EVENT_UNKNOW", "UNKNOW", 2);

    public final String code;
    public final String name;
    public final int id;

    LockEventCodeEnum(String code, String name, int id) {
        this.code = code;
        this.name = name;
        this.id = id;
    }

    public static LockEventCodeEnum getByName(String name) {
        for(LockEventCodeEnum e : values()) {
            if(e.name.equals(name)) return e;
        }
        return LOCK_EVENT_UNKNOW;
    }

    public static LockEventCodeEnum getById(Integer id) {
        for(LockEventCodeEnum e : values()) {
            if(e.id == id) return e;
        }
        return LOCK_EVENT_UNKNOW;
    }
}