package dev.niceone.smartsiteappserver.payload;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter @Setter
public class MyHomeResponse {
    private Long id;
    private int countAllSite;
    private int countAllCabinet;
    private int countAllActiveAlarm;

    private List<MyRegionResponse> myRegionList;
    private List<MyRegionResponse> myRegionResponseList;
}
