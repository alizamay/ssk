package dev.niceone.smartsiteappserver.mapper;

import dev.niceone.smartsiteappserver.model.User;
import dev.niceone.smartsiteappserver.payload.RegionResponse;
import dev.niceone.smartsiteappserver.payload.SiteMiniResponse;
import dev.niceone.smartsiteappserver.payload.UserResponse;

import java.util.List;
import java.util.stream.Collectors;

public class UserModelMapper {
    public static UserResponse mapUserResponse(User user) {
        UserResponse userResponse = new UserResponse();

        userResponse.setId(user.getId());
        userResponse.setUsername(user.getUsername());
        userResponse.setFirstname(user.getFirstname());
        userResponse.setLastname(user.getLastname());
        userResponse.setEmail(user.getEmail());
        userResponse.setActive(user.getActive());
        userResponse.setRoles(user.getRoles());

        // REGION
        List<RegionResponse> regions = user.getRegions().stream().map(region -> {
            RegionResponse regionResponse = new RegionResponse();
            regionResponse.setId(region.getId());
            regionResponse.setName(region.getName());
            return regionResponse;
        }).collect(Collectors.toList());
        userResponse.setRegions(regions);

        // SITE
        List<SiteMiniResponse> sites = user.getSites().stream().map(site -> {
            SiteMiniResponse siteResponse = new SiteMiniResponse();
            siteResponse.setId(site.getId());
            siteResponse.setName_th(site.getName_th());
            return siteResponse;
        }).collect(Collectors.toList());
        userResponse.setSites(sites);

        return userResponse;
    }
}
