package dev.niceone.smartsiteappserver.payload.request;

import lombok.Getter;
import lombok.Setter;

@Getter
public class SiteActivateRequest {
    private String code;
    private String ip_address;
    private int port;
}
