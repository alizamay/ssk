package dev.niceone.smartsiteappserver.payload.response;

import dev.niceone.smartsiteappserver.payload.DeviceResponse;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
public class HealthCheckResponse {
    private List<DeviceHealthCheckResponse> devices;
    private String message;
}
