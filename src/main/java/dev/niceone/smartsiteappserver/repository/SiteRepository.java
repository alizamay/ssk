package dev.niceone.smartsiteappserver.repository;

import dev.niceone.smartsiteappserver.model.Region;
import dev.niceone.smartsiteappserver.model.Site;
import dev.niceone.smartsiteappserver.model.constant.StatusCodeEnum;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SiteRepository extends JpaRepository<Site, Long> , JpaSpecificationExecutor<Site>, SiteRepositoryCustom {

//    @Cacheable(value = "siteCacheByCode", key = "#code", unless="#result == null")
//    Optional<Site> findByCodeAndActiveIsTrue(String code);
// ใช้ cache แล้วเจอปัญหา site กลับมา active เอง
//    @Cacheable(value = "siteCacheByCode", key = "#code", unless="#result == null")

    Optional<Site> findByCode(String code);

    Page<Site> findByRegionCode(String region, Pageable pageable);
    List<Site> findByIdIn(List<Long> ids);
    List<Site> findByActive(Boolean active);
    List<Site> findByActiveAndStatus(Boolean active, StatusCodeEnum status);
    List<Site> findByActiveAndStatusAndRegion(Boolean active, StatusCodeEnum status, Region region);
    List<Site> findByActiveAndIsInstallAndReportStatusAtGreaterThan(Boolean active, Boolean isInstall, LocalDateTime targetDateTime);
    List<Site> findByActiveAndIsInstallAndReportStatusAtLessThan(Boolean active, Boolean isInstall, LocalDateTime targetDateTime);
    List<Site> findByActiveAndIsInstallAndReportStatusAtIsNullAndStatusIsNot(Boolean active, Boolean isInstall, StatusCodeEnum status);
    @Query(value ="Select region_id from smartsite.site WHERE code = :code", nativeQuery = true)
    Long findRegionIdBySiteId(String code);
    int countByRegionAndActive(Region region, boolean active);

    @Modifying
    @Transactional
    @Query("UPDATE Site s SET s.status = 0, s.reportStatusAt = s.updatedAt, s.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE s.active = true AND s.status = 1 AND s.isInstall = true AND s.reportStatusAt < :offsetTime")
    int updateStatusAndTimestamps(@Param("offsetTime") LocalDateTime offsetTime);

    @Query("SELECT s FROM Site s JOIN FETCH s.province WHERE s.code = :code")
    Optional<Site> findByCodeWithProvince(@Param("code") String code);

    List<Site> findByActiveAndIsInstall(Boolean active, Boolean isInstall);

    List<Site> findByIsInstall(Boolean isInstall);

    @Query("SELECT s FROM Site s JOIN FETCH s.region WHERE s.code = :code")
    Optional<Site> findByCodeWithRegion(@Param("code") String code);

    // Query เพื่อหา site IDs จาก region IDs
    @Query("SELECT DISTINCT s.id FROM Site s WHERE s.region.id IN :regionIds")
    List<Long> findSiteIdsByRegionIds(@Param("regionIds") List<Long> regionIds);

    // Query เพื่อ fetch sites พร้อม JOIN FETCH related entities
    @Query("SELECT DISTINCT s FROM Site s " +
           "LEFT JOIN FETCH s.region " +
           "LEFT JOIN FETCH s.province " +
           "LEFT JOIN FETCH s.district " +
           "LEFT JOIN FETCH s.sub_district " +
           "WHERE s.id IN :siteIds " +
           "ORDER BY s.id DESC")
    List<Site> findAllWithDetailsBySiteIds(@Param("siteIds") List<Long> siteIds);
    
    // หา sites สำหรับ user โดยใช้ EXISTS (ไม่ใช้ JOIN FETCH เพื่อให้ count query ทำงานได้)
    @Query("SELECT DISTINCT s FROM Site s " +
           "WHERE (EXISTS (SELECT 1 FROM User u JOIN u.regions ur WHERE u.id = :userId AND ur.id = s.region.id) " +
           "OR EXISTS (SELECT 1 FROM User u JOIN u.sites us WHERE u.id = :userId AND us.id = s.id))")
    Page<Site> findSitesForUser(@Param("userId") Long userId, Pageable pageable);

    // Version summary counts (join site_version table)
    @Query(value = "SELECT COUNT(DISTINCT s.id) FROM smartsite.site s " +
           "JOIN smartsite.site_version sv ON sv.site_id = s.id AND sv.active = true " +
           "WHERE s.active = true AND sv.version_name = 'Android'", nativeQuery = true)
    long countActiveAndroid();

    @Query(value = "SELECT COUNT(DISTINCT s.id) FROM smartsite.site s " +
           "JOIN smartsite.site_version sv ON sv.site_id = s.id AND sv.active = true " +
           "WHERE s.active = true AND sv.version_name = 'RaspberryPi'", nativeQuery = true)
    long countActivePi();

    @Query("SELECT COUNT(s) FROM Site s WHERE s.active = true")
    long countActive();

    // Custom methods จะอยู่ใน SiteRepositoryCustom interface
}
