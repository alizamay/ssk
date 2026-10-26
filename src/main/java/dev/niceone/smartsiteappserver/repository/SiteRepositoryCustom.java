package dev.niceone.smartsiteappserver.repository;

import dev.niceone.smartsiteappserver.payload.SiteResponse;

import java.util.List;
import java.util.Map;

public interface SiteRepositoryCustom {
    List<SiteResponse> findSitesForUserOptimized(Long userId, int page, int size, String status, Long regionId, String keyword, Boolean active);
    long countSitesForUser(Long userId, String status, Long regionId, String keyword, Boolean active);
    void loadCabinetsForSites(List<SiteResponse> siteResponses);
}