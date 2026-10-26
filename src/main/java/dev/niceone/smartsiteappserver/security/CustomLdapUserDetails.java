package dev.niceone.smartsiteappserver.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.ldap.userdetails.LdapUserDetails;

import java.util.Collection;

@Slf4j
public class CustomLdapUserDetails implements LdapUserDetails {
    private static final long serialVersionUID = 1L;

    private LdapUserDetails details;

    public CustomLdapUserDetails(LdapUserDetails details) {
        this.details = details;
    }

    public boolean isEnabled() {
        return details.isEnabled();
    }

    public String getDn() {
        return details.getDn();
    }

    public Collection<? extends GrantedAuthority> getAuthorities() {
        return details.getAuthorities();
    }

    public String getPassword() {
        return details.getPassword();
    }

    public String getUsername() {
        log.error("getUsername !! "+ details.getUsername());
        return details.getUsername();
    }

    public boolean isAccountNonExpired() {
        return details.isAccountNonExpired();
    }

    public boolean isAccountNonLocked() {
        return details.isAccountNonLocked();
    }

    public boolean isCredentialsNonExpired() {
        return details.isCredentialsNonExpired();
    }

    @Override
    public void eraseCredentials() {
        log.error("eraseCredentials detect !!");
    }
}