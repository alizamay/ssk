package dev.niceone.smartsiteappserver.model;

public enum DeviceTypeCodeOld {
    DOOR_SWITCH("DOOR_SWITCH", 7),
    KEY_SWITCH("KEY_SWITCH", 10);

    public final String name;
    public final int id;

    DeviceTypeCodeOld(String name, int oldId) {
        this.name = name;
        this.id = oldId;
    }
}
