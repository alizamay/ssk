package dev.niceone.smartsiteappserver.mapper;

import dev.niceone.smartsiteappserver.model.Region;
import dev.niceone.smartsiteappserver.payload.RegionResponse;
import dev.niceone.smartsiteappserver.payload.SiteMiniResponse;

import java.util.List;
import java.util.stream.Collectors;

public class RegionModelMapper {
    public static RegionResponse mapRegionToRegionResponse(Region region) {
        RegionResponse regionResponse = new RegionResponse();
        regionResponse.setId(region.getId());
        regionResponse.setName(region.getName());

        List<SiteMiniResponse> regionSites = region.getSites().stream().map(site -> {
            SiteMiniResponse siteResponse = new SiteMiniResponse();
            siteResponse.setId(site.getId());
            siteResponse.setName_th(site.getName_th());
            return siteResponse;
        }).collect(Collectors.toList());

        regionResponse.setSites(regionSites);

        return regionResponse;
    }
}
