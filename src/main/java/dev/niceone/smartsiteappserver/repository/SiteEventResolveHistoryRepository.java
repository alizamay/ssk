package dev.niceone.smartsiteappserver.repository;

import dev.niceone.smartsiteappserver.model.SiteEventResolveHistory;
import dev.niceone.smartsiteappserver.model.SiteEventResolveHistoryId;
import dev.niceone.smartsiteappserver.model.SiteEventStatSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SiteEventResolveHistoryRepository extends JpaRepository<SiteEventResolveHistory, Long>, SiteEventResolveHistoryRepositoryCustom {
    List<SiteEventResolveHistory> findByLogId(UUID logId);
    List<SiteEventResolveHistory> findByLogIdIn(List<UUID> logIds);
}

