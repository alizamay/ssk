package dev.niceone.smartsiteappserver.repository;

import dev.niceone.smartsiteappserver.model.SiteEventLog;
import dev.niceone.smartsiteappserver.model.SiteEventLogWithCount;
import dev.niceone.smartsiteappserver.model.SiteEventLogWithResolve;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;

public interface SiteEventLogCustomRepository {
    List<SiteEventLogWithCount> getAllUnresolvedLogsWithCounts(String keyword, LocalDateTime start, LocalDateTime end);
    Page<SiteEventLog> findResolvedLogsWithFilters(
            String keyword,
            List<Integer> alarmTypes,
            LocalDateTime start,
            LocalDateTime end,
            int page,
            int size
    );

    List<SiteEventLogWithResolve> getAllResolvedLogsByAlarmTypes(String keyword, List<Integer> alarmTypes, LocalDateTime start, LocalDateTime end);
}
