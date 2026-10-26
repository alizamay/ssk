package dev.niceone.smartsiteappserver.payload.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class StatusReportRequest {
    private String site_code;
    private String site_version;
    private Double site_temperature;
    private List<ReportDeviceRequest> devices;
}
