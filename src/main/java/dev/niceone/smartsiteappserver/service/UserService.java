package dev.niceone.smartsiteappserver.service;

import dev.niceone.smartsiteappserver.exception.AppException;
import dev.niceone.smartsiteappserver.exception.ResourceNotFoundException;
import dev.niceone.smartsiteappserver.mapper.UserModelMapper;
import dev.niceone.smartsiteappserver.model.Region;
import dev.niceone.smartsiteappserver.model.Role;
import dev.niceone.smartsiteappserver.model.Site;
import dev.niceone.smartsiteappserver.model.User;
import dev.niceone.smartsiteappserver.model.constant.LockEventCodeEnum;
import dev.niceone.smartsiteappserver.model.constant.StatusCodeEnum;
import dev.niceone.smartsiteappserver.payload.*;
import dev.niceone.smartsiteappserver.repository.*;
import dev.niceone.smartsiteappserver.security.CurrentUser;
import dev.niceone.smartsiteappserver.security.UserPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import dev.niceone.smartsiteappserver.utils.SimpleCache;

import java.net.URI;
import java.util.*;

@Slf4j
@Service
public class UserService {
    private AlarmLogRepository alarmLogRepository;
    private CabinetRepository cabinetRepository;
    private RegionRepository regionRepository;
    private RoleRepository roleRepository;
    private SiteRepository siteRepository;
    private UserRepository userRepository;
    private SimpleCache<Long, MyHomeResponse> myHomeCache;
    
    @Value("${app.myhome.cache.ttl.minutes:5}")
    private int cacheTimeToLiveMinutes;

    private PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Autowired
    public UserService(AlarmLogRepository alarmLogRepository,
                       CabinetRepository cabinetRepository,
                       RegionRepository regionRepository,
                       RoleRepository roleRepository,
                       SiteRepository siteRepository,
                       UserRepository userRepository,
                       SimpleCache<Long, MyHomeResponse> myHomeCache) {
        this.alarmLogRepository = alarmLogRepository;
        this.cabinetRepository = cabinetRepository;
        this.regionRepository = regionRepository;
        this.roleRepository = roleRepository;
        this.siteRepository = siteRepository;
        this.userRepository = userRepository;
        this.myHomeCache = myHomeCache;
    }

    // Get
    public PagedResponse<UserResponse> getAll(Specification<User> spec, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.Direction.DESC, "createdAt");
        Page<User> users = userRepository.findAll(spec, pageable);

        if (users.getNumberOfElements() == 0) {
            return new PagedResponse<>(Collections.emptyList(), users.getNumber(),
                    users.getSize(), users.getTotalElements(), users.getTotalPages(), users.isLast());
        }

        List<UserResponse> userResponses = users.map(user -> {
            return UserModelMapper.mapUserResponse(user);
        }).getContent();

        return new PagedResponse<>(userResponses, users.getNumber(),
                users.getSize(), users.getTotalElements(), users.getTotalPages(), users.isLast());
    }

    public MyHomeResponse getMyHomeData(@CurrentUser UserPrincipal currentUser){
        Long userId = currentUser.getId();
        
        // Check cache first
        MyHomeResponse cachedResponse = myHomeCache.get(userId);
        if (cachedResponse != null) {
            log.info("MyHomeData loaded from cache for user: {}", userId);
            return cachedResponse;
        }
        
        log.info("Loading MyHomeData from database for user: {}", userId);
        
        // Use optimized single query method instead of N+1 queries
        MyHomeResponse myHomeResponse = userRepository.getMyHomeDataOptimized(userId);
        myHomeResponse.setId(userId);
        
        // Set the field name correctly for backward compatibility
        myHomeResponse.setMyRegionList(myHomeResponse.getMyRegionResponseList());
        
        // Cache with configured TTL
        myHomeCache.put(userId, myHomeResponse, cacheTimeToLiveMinutes);
        
        log.info("MyHomeData loaded successfully for user: {}", userId);
        return myHomeResponse;
    }

    // Save
    private ResponseEntity<ApiResponse> saveUserObject(UserRequest userRequest, User user, String action) {
        if (action == "DELETE") {
            user.setActive(false);
        } else {
            user.setActive(true);

            // ROLE
            Set<Role> tmpRoles = new HashSet<Role>();
            userRequest.getRole_list().forEach((r) -> {
                Role role = roleRepository.findById(r.getId())
                        .orElseThrow(() -> new AppException("Role not found."));
                tmpRoles.add(role);
            });
            user.setRoles(tmpRoles);

            // REGION
            Set<Region> tmpRegions = new HashSet<Region>();
            userRequest.getRegion_list().forEach((r) -> {
                Region region = regionRepository.findById(r.getId())
                        .orElseThrow(() -> new AppException("Region not found."));
                tmpRegions.add(region);
            });
            user.setRegions(tmpRegions);

            // SITE
            Set<Site> tmpSites = new HashSet<Site>();
            userRequest.getSite_list().forEach((s) -> {
                Site site = siteRepository.findById(s.getId())
                        .orElseThrow(() -> new AppException("Site not found."));
                tmpSites.add(site);
            });
            user.setSites(tmpSites);
        }

        if (action == "CREATE") {
            user.setUsername(userRequest.getUsername());
            user.setPassword(passwordEncoder().encode(userRequest.getPassword()));
            user.setFirstname(userRequest.getFirstname());
            user.setLastname(userRequest.getLastname());
            user.setEmail(userRequest.getEmail());
        }

        userRepository.save(user);

        if (action == "CREATE") {
            URI location = ServletUriComponentsBuilder
                    .fromCurrentRequest().path("/{id}")
                    .buildAndExpand(user.getId()).toUri();

            return ResponseEntity.created(location)
                    .body(new ApiResponse(true, "User Created Successfully"));
        } else {
            return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse(true, "UPDATE OK"));
        }
    }

    public ResponseEntity<ApiResponse> createUser(UserRequest userRequest) {
        User user = userRepository.findByUsername(userRequest.getUsername()).orElse(null);
        if (user == null) {
            user = new User();
        } else {
            if (user.getActive()) {
                return ResponseEntity.badRequest().body(new ApiResponse(false, "Duplicate Username"));
            }
        }
        return saveUserObject(userRequest, user, "CREATE");
    }

    public ResponseEntity<ApiResponse> updateUser(Long id, UserRequest userRequest) {
        User user = userRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("User", "id", id));
        return saveUserObject(userRequest, user, "UPDATE");
    }

    public ResponseEntity<ApiResponse> deleteUser(Long id, UserRequest userRequest) {
        User user = userRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("User", "id", id));
        return saveUserObject(userRequest, user, "DELETE");
    }
}
