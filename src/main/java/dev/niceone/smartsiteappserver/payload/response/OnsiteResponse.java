package dev.niceone.smartsiteappserver.payload.response;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class OnsiteResponse {
    private String site_code;
    private int status;
    private String raw;

    public OnsiteResponse(String site_code, int is_onsite, String raw) {
        this.site_code = site_code;
        this.status = is_onsite;
        this.raw = raw;
    }
}
