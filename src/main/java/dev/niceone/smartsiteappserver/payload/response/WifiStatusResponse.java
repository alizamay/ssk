package dev.niceone.smartsiteappserver.payload.response;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class WifiStatusResponse {
    private Boolean is_on;

    public WifiStatusResponse(boolean is_on) {
        this.is_on = is_on;
    }
}
