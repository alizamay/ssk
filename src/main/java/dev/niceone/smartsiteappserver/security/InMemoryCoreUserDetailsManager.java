package dev.niceone.smartsiteappserver.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.util.Assert;

import java.util.HashMap;
import java.util.Map;

public class InMemoryCoreUserDetailsManager implements UserDetailsManager {

    private final Map<String, UserPrincipal> users = new HashMap<>();

    @Override
    public void createUser(UserDetails user) {
        Assert.isTrue(!userExists(user.getUsername()));

        users.put(user.getUsername().toLowerCase(), (UserPrincipal) user);
    }

    @Override
    public void updateUser(UserDetails user) {
        Assert.isTrue(userExists(user.getUsername()));

        users.put(user.getUsername().toLowerCase(), (UserPrincipal) user);
    }

    @Override
    public void deleteUser(String username) {
        users.remove(username.toLowerCase());
    }

    @Override
    public void changePassword(String oldPassword, String newPassword) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean userExists(String username) {
        return users.containsKey(username.toLowerCase());
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserPrincipal user = users.get(username.toLowerCase());

        if (user == null) {
            throw new UsernameNotFoundException(username);
        }

        return user;
    }
}