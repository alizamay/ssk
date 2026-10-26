package dev.niceone.smartsiteappserver.payload.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OldImgUploadRequest {
    private String site_code;
    private String cabinet_code;
    private String dt;
    private String img_data;
    private String capture_time;
    private String device_type_id;

}
