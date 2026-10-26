package dev.niceone.smartsiteappserver.repository;

import dev.niceone.smartsiteappserver.model.SiteOnlineStatusLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalTime;
import java.util.List;

@Repository
public interface SiteOnlineStatusLogRepository extends JpaRepository<SiteOnlineStatusLog, Long> {

    @Query("SELECT s FROM SiteOnlineStatusLog s WHERE s.status = false AND s.isPingIp = false AND FUNCTION('DATE', s.checkTime) = CURRENT_DATE AND s.period = :period")
    List<SiteOnlineStatusLog> findByStatusFalseAndTodayPeriod(@Param("period") LocalTime period);

    @Query("SELECT s FROM SiteOnlineStatusLog s WHERE s.status = true AND s.isCallHelpcheck = false AND FUNCTION('DATE', s.checkTime) = CURRENT_DATE AND s.period = :period")
    List<SiteOnlineStatusLog> findByStatusTrueAndTodayPeriod(@Param("period") LocalTime period);

    @Query("SELECT s FROM SiteOnlineStatusLog s WHERE s.status = false AND s.isPingIp = false AND FUNCTION('DATE', s.checkTime) = CURRENT_DATE AND s.period = :period")
    List<SiteOnlineStatusLog> findByStatusFalseAndTodayPeriod(@Param("period") LocalTime period, Pageable pageable);

    @Query("SELECT s FROM SiteOnlineStatusLog s WHERE s.status = true AND s.isCallHelpcheck = false AND FUNCTION('DATE', s.checkTime) = CURRENT_DATE AND s.period = :period")
    List<SiteOnlineStatusLog> findByStatusTrueAndTodayPeriod(@Param("period") LocalTime period, Pageable pageable);
    
    // Count records for today with specific period
    @Query("SELECT COUNT(s) FROM SiteOnlineStatusLog s WHERE FUNCTION('DATE', s.checkTime) = CURRENT_DATE AND s.period = :period")
    long countByTodayAndPeriod(@Param("period") LocalTime period);
    
    // Delete records for today with specific period
    @Query("DELETE FROM SiteOnlineStatusLog s WHERE FUNCTION('DATE', s.checkTime) = CURRENT_DATE AND s.period = :period")
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    int deleteByTodayAndPeriod(@Param("period") LocalTime period);
}
