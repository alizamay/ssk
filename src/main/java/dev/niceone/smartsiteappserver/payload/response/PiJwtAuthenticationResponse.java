package dev.niceone.smartsiteappserver.payload.response;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class PiJwtAuthenticationResponse {
    private String accessToken;
    private String tokenType = "Rpi";

    public PiJwtAuthenticationResponse(String accessToken) {
        this.accessToken = accessToken;
    }

}
