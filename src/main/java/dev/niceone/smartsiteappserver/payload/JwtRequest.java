package dev.niceone.smartsiteappserver.payload;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JwtRequest {
    private String accessToken;
    private String tokenType = "Bearer";
}
