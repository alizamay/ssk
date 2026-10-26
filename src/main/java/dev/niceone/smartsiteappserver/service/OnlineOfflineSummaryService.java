package dev.niceone.smartsiteappserver.service;

import dev.niceone.smartsiteappserver.model.OnlineOfflineSummaryDaily;
import dev.niceone.smartsiteappserver.model.OnlineStatusDetailResponse;
import dev.niceone.smartsiteappserver.model.OnlineStatusResponse;
import dev.niceone.smartsiteappserver.repository.OnlineStatusRepositoryCustom;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;


import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OnlineOfflineSummaryService {

    private final OnlineStatusRepositoryCustom repository;
    public List<OnlineOfflineSummaryDaily> getDailySummary(LocalDateTime start, LocalDateTime end) {
        List<OnlineOfflineSummaryDaily> results = repository.getDailySummaryCounts(start,end);
        return results;
    }

    public List<OnlineStatusResponse> getOnlineStatusesByDate(
            String todayDate,
            String siteCode,
            String regionCode,
            String provinceCode,
            Boolean onlineStatus
    ) {
        return repository.findTodayOnlineStatuses(
                todayDate,
                siteCode,
                regionCode,
                provinceCode,
                onlineStatus
        );
    }


    public List<OnlineStatusDetailResponse> getOnlineOfflineDetailStatusesByDate(
            String start,
            String end,
            String siteCode,
            String regionCode,
            String provinceCode,
            Boolean onlineStatus
    ) {
        return repository.findOnlineOfflineCheckDetailStatuses(
                start,
                end,
                siteCode,
                regionCode,
                provinceCode,
                onlineStatus
        );
    }

    public Page<OnlineStatusResponse> getOnlineStatusesByDatePaged(
            String todayDate,
            String siteCode,
            String regionCode,
            String provinceCode,
            Boolean onlineStatus,
            Pageable pageable
    ) {
        return repository.findTodayOnlineStatusesPaged(
                todayDate, siteCode, regionCode, provinceCode, onlineStatus, pageable
        );
    }

    public Page<OnlineStatusDetailResponse> getOnlineOfflineDetailStatusesByDatePaged(
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
    ) {
        return repository.findOnlineOfflineCheckDetailStatusesPaged(
                start, end, siteCode, regionCode, provinceCode,
                onlineStatus, pingIpStatus, pingGwStatus, healthCheckOnly, pageable
        );
    }
}
