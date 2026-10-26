package dev.niceone.smartsiteappserver.security;

import dev.niceone.smartsiteappserver.model.User;
import dev.niceone.smartsiteappserver.exception.ResourceNotFoundException;
import dev.niceone.smartsiteappserver.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    UserRepository userRepository;

    public UserDetails createUserInOurDB(String username, String firstname, String lastname) {
        log.info("createUserInOurDB: "+username);
        User user = new User(username, "password", firstname, lastname);
        userRepository.save(user);
        return UserPrincipal.create(user);
    }

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String usernameOrEmail)
            throws UsernameNotFoundException {
        // Let people login with either username or email
//        log.info("loadUserByUsername "+usernameOrEmail);
        User user = userRepository.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail)
        .orElseThrow(() ->
                new UsernameNotFoundException("User not found with username or email : " + usernameOrEmail)
        );

        return UserPrincipal.create(user);
    }

    public Boolean isUsernameExistInOurDB(String usernameOrEmail) {
        boolean result = false;
        Optional<User> user = userRepository.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail);
        log.info("isUsernameExistInOurDB "+user);
        if (user.isPresent()){
            result = true;
        }
        return result;
    }

    @Transactional
    public UserDetails loadUserById(Long id) {
        log.info("loadUserById "+id);
        User user = userRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("User", "id", id)
        );

        return UserPrincipal.create(user);
    }
}
