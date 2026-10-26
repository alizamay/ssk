package dev.niceone.smartsiteappserver.payload;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class AlarmTypeResponse {
    private Long id;
    private String name_th;
    private String name_en;
    private int serverity;
}
