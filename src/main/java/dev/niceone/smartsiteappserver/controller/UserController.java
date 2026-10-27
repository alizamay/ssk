package dev.niceone.smartsiteappserver.controller;

import dev.niceone.smartsiteappserver.model.User;
import dev.niceone.smartsiteappserver.payload.*;
import dev.niceone.smartsiteappserver.repository.UserRepository;
import dev.niceone.smartsiteappserver.security.CurrentUser;
import dev.niceone.smartsiteappserver.security.UserPrincipal;
import dev.niceone.smartsiteappserver.service.UserService;
import lombok.extern.slf4j.Slf4j;
import net.kaczmarzyk.spring.data.jpa.domain.Equal;
import net.kaczmarzyk.spring.data.jpa.domain.LikeIgnoreCase;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Conjunction;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Or;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Spec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Conjunction(value = {
        @Or({@Spec(path = "firstname", params = "keyword", spec = LikeIgnoreCase.class),
                @Spec(path = "lastname", params = "keyword", spec = LikeIgnoreCase.class),
                @Spec(path = "email", params = "keyword", spec = LikeIgnoreCase.class)}),
}, and = @Spec(path="active", params = "active", spec= Equal.class))
interface UserSpec1 extends Specification<User> {
}

@RestController
@Slf4j
@RequestMapping("/api")
public class UserController {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    // Get all
    @Autowired
    private ObjectMapper objectMapper;

    // Serialises the whole User -> sites -> cabinets -> devices graph. With open-in-view off,
    // writing it inside one read-only transaction keeps a single Hibernate session (shared
    // first-level cache) instead of a new session per lazy association.
    @GetMapping("/user/list")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public void findAll(HttpServletResponse response) throws IOException {
        List<User> users = userRepository.findByActive(true);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), users);
    }

    @GetMapping("/user/list/v2")
    public PagedResponse<UserResponse> filter4(
            @RequestParam(name="region", required=false) String region,
            @RequestParam(name="keyword", required=false) String keyword,
            @RequestParam(value = "pageNumber", defaultValue = "0") int pageNumber,
            @RequestParam(value = "pageSize", defaultValue = "30") int pageSize,
            UserSpec1 spec1) {

        return userService.getAll(spec1, pageNumber, pageSize);
    }

    @GetMapping("/user/me")
    @PreAuthorize("hasRole('USER')")
    public UserSummary getCurrentUser(@CurrentUser UserPrincipal currentUser) {
        UserSummary userSummary = new UserSummary(currentUser.getId(),
                currentUser.getUsername(),
                currentUser.getFirstname(),
                currentUser.getRawUser().getRoles());
        return userSummary;
    }

    @GetMapping("/my-home/")
    @PreAuthorize("hasRole('USER')")
    public MyHomeResponse myHome(@CurrentUser UserPrincipal currentUser) {
        return userService.getMyHomeData(currentUser);
    }

    // Save
    @PostMapping("/user")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<?> createUser(@Valid @RequestBody UserRequest userRequest) {
        ResponseEntity<ApiResponse> response = userService.createUser(userRequest);
        return response;
    }

    // Update
    @PutMapping("/user/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    ResponseEntity<?> updateUser(@Valid @RequestBody UserRequest userRequest, @PathVariable Long id) {
        return userService.updateUser(id, userRequest);
    }

    // Delete
    @PutMapping("/user/delete/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    ResponseEntity<?> deleteUser(@Valid @RequestBody UserRequest userRequest, @PathVariable Long id) {
        return userService.deleteUser(id, userRequest);
    }

    @GetMapping("/user/checkUsernameAvailability")
    public UserIdentityAvailability checkUsernameAvailability(@RequestParam(value = "username") String username) {
        Boolean isAvailable = !userRepository.existsByUsername(username);
        return new UserIdentityAvailability(isAvailable);
    }

    @GetMapping("/user/checkEmailAvailability")
    public UserIdentityAvailability checkEmailAvailability(@RequestParam(value = "email") String email) {
        Boolean isAvailable = !userRepository.existsByEmail(email);
        return new UserIdentityAvailability(isAvailable);
    }

//    @GetMapping("/users/{username}")
//    public UserProfile getUserProfile(@PathVariable(value = "username") String username) {
//        User user = userRepository.findByUsername(username)
//                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
//
//        long pollCount = pollRepository.countByCreatedBy(user.getId());
//        long voteCount = voteRepository.countByUserId(user.getId());
//
//        UserProfile userProfile = new UserProfile(user.getId(), user.getUsername(), user.getName(), user.getCreatedAt(), pollCount, voteCount);
//
//        return userProfile;
//    }
//
//    @GetMapping("/users/{username}/polls")
//    public PagedResponse<PollResponse> getPollsCreatedBy(@PathVariable(value = "username") String username,
//                                                         @CurrentUser UserPrincipal currentUser,
//                                                         @RequestParam(value = "page", defaultValue = AppConstants.DEFAULT_PAGE_NUMBER) int page,
//                                                         @RequestParam(value = "size", defaultValue = AppConstants.DEFAULT_PAGE_SIZE) int size) {
//        return pollService.getPollsCreatedBy(username, currentUser, page, size);
//    }
//
//
//    @GetMapping("/users/{username}/votes")
//    public PagedResponse<PollResponse> getPollsVotedBy(@PathVariable(value = "username") String username,
//                                                       @CurrentUser UserPrincipal currentUser,
//                                                       @RequestParam(value = "page", defaultValue = AppConstants.DEFAULT_PAGE_NUMBER) int page,
//                                                       @RequestParam(value = "size", defaultValue = AppConstants.DEFAULT_PAGE_SIZE) int size) {
//        return pollService.getPollsVotedBy(username, currentUser, page, size);
//    }

}