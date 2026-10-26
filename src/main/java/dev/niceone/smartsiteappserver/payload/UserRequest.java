package dev.niceone.smartsiteappserver.payload;

import lombok.Getter;
import lombok.ToString;

import java.util.List;

@Getter
@ToString
public class UserRequest {
    private String username;
    private String password;
    private String firstname;
    private String lastname;
    private String email;
    private List<RegionRequest> region_list;
    private List<SiteRequest> site_list;
    private List<RoleRequest> role_list;
}
