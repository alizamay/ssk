package dev.niceone.smartsiteappserver.repository;

import dev.niceone.smartsiteappserver.model.SiteEventStatSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SiteEventStatSummaryRepository extends JpaRepository<SiteEventStatSummary, Long> {
    Optional<SiteEventStatSummary> findByLogIdAndDate(UUID logId, LocalDate date);
    List<SiteEventStatSummary> findAllByDate(LocalDate date);
    Optional<SiteEventStatSummary> findFirstByLogId(UUID logId);
    List<SiteEventStatSummary> findByLogIdIn(List<UUID> logIds);
}
