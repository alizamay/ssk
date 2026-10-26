package dev.niceone.smartsiteappserver.controller;

import dev.niceone.smartsiteappserver.exception.AppException;
import dev.niceone.smartsiteappserver.model.*;
import dev.niceone.smartsiteappserver.payload.*;
import dev.niceone.smartsiteappserver.payload.request.SiteActivateRequest;
import dev.niceone.smartsiteappserver.payload.response.PiJwtAuthenticationResponse;
import dev.niceone.smartsiteappserver.elastic.repository.ActivityLogRepository;
import dev.niceone.smartsiteappserver.repository.RoleRepository;
import dev.niceone.smartsiteappserver.repository.UserRepository;
import dev.niceone.smartsiteappserver.security.CustomUserDetailsService;
import dev.niceone.smartsiteappserver.security.JwtTokenProvider;
import dev.niceone.smartsiteappserver.security.PiJwtTokenProvider;
import dev.niceone.smartsiteappserver.security.UserPrincipal;
import dev.niceone.smartsiteappserver.service.SiteService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.validation.Valid;
import java.net.URI;
import java.util.*;

@RestController
@RequestMapping("/api/auth")
@Slf4j
public class AuthController {
    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private PiJwtTokenProvider piTokenProvider;

    @Autowired
    private CustomUserDetailsService userServices;

    @Autowired
    private SiteService siteService;

    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        String messageAttempt = loginRequest.getUsernameOrEmail() + " try login.";
        ActivityLog activityLogAttempt = new ActivityLog("Web GUI Login Attempt", "USER", messageAttempt,
                "DEBUG", "");
//        activityLogRepository.save(activityLogAttempt);

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsernameOrEmail(),
                        loginRequest.getPassword()
                )
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = tokenProvider.generateToken(authentication);

        String message = loginRequest.getUsernameOrEmail() + " login success.";
        ActivityLog activityLog = new ActivityLog("Web GUI Login Result", "USER", message,
                "DEBUG", "Success");
//        activityLogRepository.save(activityLog);

        return ResponseEntity.ok(new JwtAuthenticationResponse(jwt));
    }

    @PostMapping("/activate")
    public ResponseEntity<?> activeUser(@Valid @RequestBody JwtRequest jwtRequest) {

        String token = jwtRequest.getAccessToken();
        UserPrincipal user = null;
        boolean isExist = false;

        if (StringUtils.hasText(token) && tokenProvider.validateToken(token)) {
            String userName = tokenProvider.getUserNameFromJWT(token);
            boolean isExistUser = userServices.isUsernameExistInOurDB(userName);

            if (isExistUser) {
                isExist = true;
                log.info("*** found exist user from JWT ***");
                try{
                    user = (UserPrincipal) userServices.loadUserByUsername(userName);
                } catch(Exception e) {
                    log.error(" : " + e.getMessage());
                }
            } else {
                log.info("*** Active new user from JWT ***");
                User newUser = new User(userName, "password", userName, userName);
                user = UserPrincipal.create(newUser);
            }
        } else {
            log.error("invalid token: "+token);
            return ResponseEntity.badRequest().body(new ApiResponse(false, "invalid token"));
        }

        URI location = ServletUriComponentsBuilder
            .fromCurrentContextPath().path("/users/{username}")
            .buildAndExpand(user.getUsername()).toUri();

        if (isExist) {
            return ResponseEntity.ok().body(new ApiResponse(true, "User updated successfully"));
        } else {
            return ResponseEntity.created(location).body(new ApiResponse(true, "User created successfully"));
        }
    }

    // DEFAULT public signup api
//    @PostMapping("/signup")
//    public ResponseEntity<?> registerUser(@Valid @RequestBody SignUpRequest signUpRequest) {
//        if (userRepository.existsByUsername(signUpRequest.getUsername())) {
//            return new ResponseEntity(new ApiResponse(false, "Username is already taken!"),
//                    HttpStatus.BAD_REQUEST);
//        }
//
//        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
//            return new ResponseEntity(new ApiResponse(false, "Email Address already in use!"),
//                    HttpStatus.BAD_REQUEST);
//        }
//
//        // Creating user's account
//        User user = new User(signUpRequest.getUsername(), signUpRequest.getPassword(), signUpRequest.getFirstname(), signUpRequest.getLastname(),
//                signUpRequest.getEmail());
//
//        Role userRole = roleRepository.findByName(RoleName.ROLE_USER)
//                .orElseThrow(() -> new AppException("User Role not set."));
//
//        user.setRoles(Collections.singleton(userRole));
//
//        User result = userRepository.save(user);
//
//        URI location = ServletUriComponentsBuilder
//                .fromCurrentContextPath().path("/users/{username}")
//                .buildAndExpand(result.getUsername()).toUri();
//
//        return ResponseEntity.created(location).body(new ApiResponse(true, "User registered successfully"));
//    }

    @PostMapping("/pi/activate")
    public ResponseEntity<?> activateSite(@Valid @RequestBody SiteActivateRequest request) {

        String code = request.getCode();
        String ip = request.getIp_address();
        int port = request.getPort();

        UserPrincipal user = null;
        boolean isExist = false;

        Site site = siteService.getSiteByCode(code).orElse(null);

        if (site == null) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "no site found"));
        }

        if (!site.getIp_address().equals(ip)) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "invalid ip"));
        }

        if (site.getPort() != port) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "invalid port"));
        }

        String jwt = piTokenProvider.generateToken(site.getCode());
        log.info("jwt: "+jwt);
        return ResponseEntity.ok(new PiJwtAuthenticationResponse(jwt));
    }
}