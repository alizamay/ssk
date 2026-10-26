package dev.niceone.smartsiteappserver.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import th.co.ais.authenldap.*;

import java.util.Collections;

@Slf4j
@Component
public class CustomAuthenticationProvider implements AuthenticationProvider {

    private PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Value("${app.ldap_skip}")
    private String ldap_skip;

    @Value("${app.ldap_endpoint}")
    private String ldap_endpoint;

    @Value("${app.ldap_projectcode}")
    private String ldap_projectcode;

    @Autowired
    private CustomUserDetailsService userServices;

    @Override
    public Authentication authenticate(Authentication authentication)
            throws AuthenticationException {

        String username = authentication.getName();
        String password = authentication.getCredentials().toString();

        if (ldap_skip.trim().equals("yes")) {
            return skipAuth(username, password);
        } else {
            return ldapAuth(username, password);
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.equals(UsernamePasswordAuthenticationToken.class);
    }

    private UsernamePasswordAuthenticationToken skipAuth(String username, String password) {
        UserPrincipal user = (UserPrincipal) userServices.loadUserByUsername(username);

        if (passwordEncoder().matches(password, user.getPassword())) {
            return new UsernamePasswordAuthenticationToken
                    (username, password, Collections.emptyList());
        }
        throw new
                BadCredentialsException("External system authentication failed");
    }

    private UsernamePasswordAuthenticationToken ldapAuth(String username, String password) {
        String ldapResMsg = null;
        ServiceResponse res = null;
        try {
            if(ldap_endpoint!=null){
                log.info("ldap_endpoint: " + ldap_endpoint);
                log.info("ldap_projectcode: " + ldap_projectcode);
                AuthenticateServiceLocator authenticateServiceLocator = new AuthenticateServiceLocator();
                authenticateServiceLocator.setAuthenticateServiceSoapEndpointAddress(ldap_endpoint);
                AuthenticateServiceSoap service = authenticateServiceLocator.getAuthenticateServiceSoap() ;
                UserAuthenticate user = new UserAuthenticate();
                user.setUsername(username);
                user.setPassword(password);
                ProjectQuery authenProject = new ProjectQuery();
                authenProject.setProjectCode(ldap_projectcode);
                authenProject.setProjectLevel(1);

                log.info("authenticate...");
                res =  service.authenticate(user, authenProject);

                if( res != null ){
                    ldapResMsg = res.getMessage();
                    log.info("got response: "+ldapResMsg);
                } else {
                    ldapResMsg = "LDAPService:authenLDAP1";
                    log.info("null response from ldap !!!");
                }

            }else{
                ldapResMsg = "LDAPService:Required Endpoint!";
                log.info("ldap_endpoint is null please check application.properties file !!!");
            }
        } catch (Exception e) {
            e.printStackTrace();
            ldapResMsg = e.toString();// "LDAPService:authenLDAP2";
            log.info("ldapAuth found exception: " + ldapResMsg);
//            ActivityLog.saveLog(
//                    "loginLDAP", "LDAP Login Error",
//                    "Input username: " + username
//                            + " LDAP msg: " + ldapResMsg
//                    , "Failed");
        }

        UserPrincipal user = null;
        try{
            user = (UserPrincipal) userServices.loadUserByUsername(username);
        } catch(Exception e) {
            log.error(" : " + e.getMessage());
            throw new
                    BadCredentialsException("User not found in smartsite2.");
        }
        if (user==null) {
            throw new
                    BadCredentialsException("User not found in smartsite2.");
        }

        log.info(ldapResMsg);
        if( ldapResMsg.equalsIgnoreCase("Success") ) {
            log.info("authentication success");
            return new UsernamePasswordAuthenticationToken
                    (username, password, Collections.emptyList());
        }
        throw new
                BadCredentialsException("AIS LDAP system authentication failed");
    }
}