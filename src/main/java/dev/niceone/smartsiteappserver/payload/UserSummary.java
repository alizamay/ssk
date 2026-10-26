package dev.niceone.smartsiteappserver.payload;

import dev.niceone.smartsiteappserver.model.Role;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter @Setter
public class UserSummary {
    private Long id;
    private String username;
    private String name;
    private Set<Role> roles;

    public UserSummary(Long id, String username, String name, Set<Role> roles) {
        this.id = id;
        this.username = username;
        this.name = name;
        this.roles = roles;
    }
}
