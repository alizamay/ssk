package dev.niceone.smartsiteappserver.repository;

import dev.niceone.smartsiteappserver.model.SiteEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SiteEventLogRepository extends JpaRepository<SiteEventLog, UUID>,
        JpaSpecificationExecutor<SiteEventLog>,
        SiteEventLogCustomRepository{
    Optional<SiteEventLog> findFirstBySiteIdAndAlarmTypeAndCabinetIdAndCreateDateBetween(
            String siteId, Integer alarmType, String cabinetId, LocalDateTime start, LocalDateTime end);

    Optional<SiteEventLog> findFirstBySiteIdAndAlarmTypeAndCreateDateBetween(
            String siteId, Integer alarmType, LocalDateTime start, LocalDateTime end);

    Optional<SiteEventLog> findFirstBySiteCodeAndAlarmTypeAndCreateDateBetween(
            String siteCode, Integer alarmType, LocalDateTime start, LocalDateTime end);

    List<SiteEventLog> findByIsResolveAndAlarmTypeAndSiteIdAndCreateDateBetween(
            Boolean isResolve,
            Integer alarmType,
            String siteId,
            LocalDateTime startDate,
            LocalDateTime endDate
    );
}
