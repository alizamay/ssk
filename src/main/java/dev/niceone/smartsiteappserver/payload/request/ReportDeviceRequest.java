package dev.niceone.smartsiteappserver.payload.request;

import dev.niceone.smartsiteappserver.model.constant.EventTypeCodeEnum;
import dev.niceone.smartsiteappserver.model.constant.StatusCodeEnum;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReportDeviceRequest {
    private String serial;
    private String cabinet_code;
    private String dt;  // "2020-07-22 15:44:29"

    private int device_status_id;
    private String device_status;

    private int device_type_id;
    private String device_type;

    private int event_type_id;
    private String event_type;
}
