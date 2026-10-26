package dev.niceone.smartsiteappserver.payload;

import dev.niceone.smartsiteappserver.model.Role;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Set;

@Getter @Setter
public class UserResponse {
    private Long id;
    private String username;
    private String firstname;
    private String lastname;
    private String email;
    private Boolean active;
    private List<RegionResponse> regions;
    private List<SiteMiniResponse> sites;
    private Set<Role> roles;
}
