package dev.niceone.smartsiteappserver.security;

import dev.niceone.smartsiteappserver.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.ldap.userdetails.LdapAuthoritiesPopulator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Service("myAuthPopulator")
@Slf4j
public class MyAuthoritiesPopulator implements LdapAuthoritiesPopulator {

    @Autowired
    private CustomUserDetailsService userServices;

    @Transactional(readOnly=true)
    @Override
    public Collection<? extends GrantedAuthority> getGrantedAuthorities(DirContextOperations userData, String username) {
        Set<GrantedAuthority> authorities = new HashSet<GrantedAuthority>();
        try{
            UserPrincipal user = (UserPrincipal) userServices.loadUserByUsername(username);
            if (user==null)
                log.error("Threw exception in MyAuthoritiesPopulator::getGrantedAuthorities : User doesn't exist into ATS database" );
            else{
                log.info("user.getAuthorities() "+user.getAuthorities());
                return user.getAuthorities();
            }
        } catch(Exception e) {
            log.error("Threw exception in MyAuthoritiesPopulator::getGrantedAuthorities : " + e.getMessage());
        }
        return authorities;
    }
}