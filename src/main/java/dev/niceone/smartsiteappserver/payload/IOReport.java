package dev.niceone.smartsiteappserver.payload;

import dev.niceone.smartsiteappserver.payload.request.ReportDeviceRequest;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class IOReport {
    private String cabinet_code;
    private Boolean auxin1;
    private Boolean auxin2;
    private Boolean auxin3;
}
