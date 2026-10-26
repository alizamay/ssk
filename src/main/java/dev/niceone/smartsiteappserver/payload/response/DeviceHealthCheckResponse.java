package dev.niceone.smartsiteappserver.payload.response;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class DeviceHealthCheckResponse {
    private String name;
    private String status;

    public DeviceHealthCheckResponse(String name, String status) {
        this.name = name;
        this.status = status;
    }
}



