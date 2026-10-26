package dev.niceone.smartsiteappserver.payload;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter @Setter
public class RegionResponse {
    private Long id;
    private String name;
    private List<SiteMiniResponse> sites;
}
