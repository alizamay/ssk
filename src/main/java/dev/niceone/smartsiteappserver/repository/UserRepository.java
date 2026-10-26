package dev.niceone.smartsiteappserver.repository;

import dev.niceone.smartsiteappserver.model.Site;
import dev.niceone.smartsiteappserver.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, UserRepositoryCustom {
    Page<User> findAll(Specification<User> spec, Pageable pageable);
    List<User> findByActive(boolean active);
    Optional<User> findByEmail(String email);
    Optional<User> findByUsernameOrEmail(String username, String email);
    Optional<User> findByUsername(String username);

    Boolean existsByUsername(String username);
    Boolean existsByEmail(String email);

    long countByIdAndRegionsActive(long id, boolean active);

    // แก้ N+1 Problem - JOIN FETCH user กับ regions และ sites
    @Query("SELECT DISTINCT u FROM User u " +
           "LEFT JOIN FETCH u.regions r " +
           "LEFT JOIN FETCH u.sites s " +
           "WHERE u.id = :userId")
    Optional<User> findByIdWithRegionsAndSites(@Param("userId") Long userId);
    
    // หา site IDs ที่ user เข้าถึงได้ (จาก regions + direct sites) - แบ่งเป็น 2 query เพื่อความชัดเจน
    @Query("SELECT DISTINCT s.id FROM Site s " +
           "WHERE s.region.id IN (SELECT r.id FROM User u JOIN u.regions r WHERE u.id = :userId)")
    List<Long> findSiteIdsByUserRegions(@Param("userId") Long userId);
    
    @Query("SELECT DISTINCT site.id FROM User u JOIN u.sites site WHERE u.id = :userId")
    List<Long> findDirectSiteIdsByUserId(@Param("userId") Long userId);

}
