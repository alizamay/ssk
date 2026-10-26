package dev.niceone.smartsiteappserver.payload.request;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class SiteCSVRequest {
    private String region;
    private String status;
    private String keyword;
}
