package dev.niceone.smartsiteappserver.repository;

import dev.niceone.smartsiteappserver.payload.MyHomeResponse;
import dev.niceone.smartsiteappserver.security.UserPrincipal;

public interface UserRepositoryCustom {
    MyHomeResponse getMyHomeDataOptimized(Long userId);
}