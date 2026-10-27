package dev.niceone.smartsiteappserver.service;

import dev.niceone.smartsiteappserver.model.OnsiteCheckLog;
import dev.niceone.smartsiteappserver.repository.OnsiteCheckLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class OnsiteCheckLogService {

    @Autowired
    private OnsiteCheckLogRepository repository;

    @Autowired
    private SchedulerLockService schedulerLockService;

    @Autowired
    private Environment env;

    // Pi boards poll /api/check/onsite several times a second (~3.5M rows/day across nodes).
    // Store a row only when the answer for a site+client changes, or at most once per interval.
    // 0 = store every request (previous behaviour).
    @Value("${app.onsite_check_log.min_interval_sec:300}")
    private long minIntervalSec;

    // Delete rows older than this many days (leader node, daily). 0 = keep everything.
    @Value("${app.onsite_check_log.retention_days:0}")
    private int retentionDays;

    private final Map<String, LastLogged> lastLogged = new ConcurrentHashMap<>();

    private static final class LastLogged {
        final int status;
        final String raw;
        final long atMillis;

        LastLogged(int status, String raw, long atMillis) {
            this.status = status;
            this.raw = raw;
            this.atMillis = atMillis;
        }
    }

    private boolean shouldStore(String siteCode, String clientIp, int responseStatus, String responseRaw) {
        if (minIntervalSec <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        boolean[] store = {false};
        lastLogged.compute(siteCode + "|" + clientIp, (key, prev) -> {
            if (prev == null || prev.status != responseStatus || !Objects.equals(prev.raw, responseRaw)
                    || now - prev.atMillis >= minIntervalSec * 1000) {
                store[0] = true;
                return new LastLogged(responseStatus, responseRaw, now);
            }
            return prev;
        });
        return store[0];
    }

    @Async
    public void save(String siteCode, String clientIp, int responseStatus, String responseRaw) {
        if (!shouldStore(siteCode, clientIp, responseStatus, responseRaw)) {
            return;
        }
        try {
            OnsiteCheckLog entry = new OnsiteCheckLog();
            entry.setSiteCode(siteCode);
            entry.setClientIp(clientIp);
            entry.setRequestTime(LocalDateTime.now());
            entry.setResponseStatus(responseStatus);
            entry.setResponseRaw(responseRaw);
            repository.save(entry);
        } catch (Exception e) {
            log.error("Failed to save OnsiteCheckLog: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "0 40 3 * * *")
    public void purgeOldRows() {
        if (retentionDays <= 0) return;
        if (!"yes".equals(env.getProperty("app.site_scheduler", "no").trim())) return;
        if (!schedulerLockService.isLeader()) return;

        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        long total = 0;
        int deleted;
        do {
            deleted = repository.deleteOlderThan(cutoff, 10_000);
            total += deleted;
        } while (deleted > 0 && schedulerLockService.isLeader());
        log.info("onsite_check_log retention: deleted {} rows older than {}", total, cutoff);
    }

    public Page<OnsiteCheckLog> findByFilters(String siteCode, LocalDateTime startTime,
                                               LocalDateTime endTime, Integer responseStatus,
                                               Pageable pageable) {
        return repository.findByFilters(siteCode, startTime, endTime, responseStatus, pageable);
    }

    public List<OnsiteCheckLog> findAllByFilters(String siteCode, LocalDateTime startTime,
                                                  LocalDateTime endTime, Integer responseStatus) {
        return repository.findAllByFilters(siteCode, startTime, endTime, responseStatus);
    }
}
