package dev.niceone.smartsiteappserver.payload;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class MyRegionResponse {
    private Long id;
    private String name;

    private int countSite;
    private int countSiteActiveAlarm;

    private int countCabinet;
    private int countCabinetOnline;
    private int countCabinetOffline;
    private int countCabinetLocked;
    private int countCabinetUnlocked;
    private int countCabinetUnknow;
}
