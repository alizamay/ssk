package dev.niceone.smartsiteappserver.payload;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class StatusResponse {
    private Integer id;
    private String code;
    private String name;
//    private Boolean active;
}
