package dev.niceone.smartsiteappserver.repository;

import dev.niceone.smartsiteappserver.model.OnlineOfflineSummaryDaily;
import dev.niceone.smartsiteappserver.model.OnlineStatusDetailResponse;
import dev.niceone.smartsiteappserver.model.OnlineStatusResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface OnlineStatusRepositoryCustom {
    Page<OnlineStatusResponse> findOnlineStatuses(String siteCode, String regionCode, String provinceCode, Boolean onlineStatus,
                                                  LocalDateTime start, LocalDateTime end, Pageable pageable);
    List<OnlineOfflineSummaryDaily> getDailySummaryCounts(LocalDateTime start, LocalDateTime end);
    List<OnlineStatusResponse> findTodayOnlineStatuses(
            String todayDate,
            String siteCode,
            String regionCode,
            String provinceCode,
            Boolean onlineStatus
    );

    Page<OnlineStatusResponse> findTodayOnlineStatusesPaged(
            String todayDate,
            String siteCode,
            String regionCode,
            String provinceCode,
            Boolean onlineStatus,
            Pageable pageable
    );

    List<OnlineStatusDetailResponse> findOnlineOfflineCheckDetailStatuses(
            String start,
            String end,
            String siteCode,
            String regionCode,
            String provinceCode,
            Boolean onlineStatus
    );

    Page<OnlineStatusDetailResponse> findOnlineOfflineCheckDetailStatusesPaged(
            String start,
            String end,
            String siteCode,
            String regionCode,
            String provinceCode,
            Boolean onlineStatus,
            String pingIpStatus,
            String pingGwStatus,
            Boolean healthCheckOnly,
            Pageable pageable
    );
}
