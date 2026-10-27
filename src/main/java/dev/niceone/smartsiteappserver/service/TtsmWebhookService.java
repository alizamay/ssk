package dev.niceone.smartsiteappserver.service;

import dev.niceone.smartsiteappserver.dto.TtsmJobStatusDto;
import dev.niceone.smartsiteappserver.model.TtsmJobStatus;
import dev.niceone.smartsiteappserver.repository.TtsmJobStatusRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class TtsmWebhookService {

    @Autowired
    private TtsmJobStatusRepository ttsmJobStatusRepository;

    private static final DateTimeFormatter ACTION_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * บันทึก Job Status ที่ TTSM ส่งมา
     * สร้าง 1 record ต่อ 1 siteCode
     */
    @Transactional
    public void saveJobStatus(TtsmJobStatusDto dto) {
        LocalDateTime actionDate = null;
        if (dto.getActionDate() != null) {
            try {
                actionDate = LocalDateTime.parse(dto.getActionDate(), ACTION_DATE_FORMATTER);
            } catch (Exception e) {
                log.warn("Could not parse actionDate: {}", dto.getActionDate());
            }
        }

        LocalDateTime finalActionDate = actionDate;
        dto.getSiteCode().forEach(siteCode -> {
            TtsmJobStatus entity = new TtsmJobStatus();
            entity.setSiteCode(siteCode);
            entity.setJobId(dto.getJobId());
            entity.setJobStatus(dto.getJobStatus());
            entity.setActionDate(finalActionDate);
            entity.setReceivedAt(LocalDateTime.now());
            ttsmJobStatusRepository.save(entity);
            evict(siteCode);
            log.info("Saved job status from TTSM | Site: {} | Job: {} | Status: {}",
                    siteCode, dto.getJobId(), dto.getJobStatus());
        });
    }

    /**
     * เช็คว่ามีช่างอยู่ onsite หรือไม่
     * Logic: มี jobId ของ site นี้ที่ latest status ยังเป็น "On-Site" อยู่ (ยังไม่ Done/Held)
     */
    public boolean isTechnicianOnsite(String siteCode) {
        long now = System.currentTimeMillis();
        Cached<Boolean> cached = onsiteCache.get(siteCode);
        if (cached == null || cached.expiresAt < now) {
            cached = new Cached<>(ttsmJobStatusRepository.existsActiveOnsiteJob(siteCode), now + CACHE_TTL_MS);
            onsiteCache.put(siteCode, cached);
        }
        return cached.value;
    }

    /**
     * ดึง Job Status ล่าสุดของ Site
     */
    public TtsmJobStatus getLatestJobStatus(String siteCode) {
        long now = System.currentTimeMillis();
        Cached<TtsmJobStatus> cached = latestCache.get(siteCode);
        if (cached == null || cached.expiresAt < now) {
            TtsmJobStatus latest = ttsmJobStatusRepository
                    .findFirstBySiteCodeOrderByReceivedAtDesc(siteCode)
                    .orElse(null);
            cached = new Cached<>(latest, now + CACHE_TTL_MS);
            latestCache.put(siteCode, cached);
        }
        return cached.value;
    }

    /**
     * Both lookups run on every askTTSM alarm request, and boards poll the same site many
     * times a minute. A webhook received on this node clears the site immediately; webhooks
     * received on other nodes become visible within the TTL.
     */
    private static final long CACHE_TTL_MS = 15_000;
    private final Map<String, Cached<Boolean>> onsiteCache = new ConcurrentHashMap<>();
    private final Map<String, Cached<TtsmJobStatus>> latestCache = new ConcurrentHashMap<>();

    private static final class Cached<T> {
        final T value;
        final long expiresAt;

        Cached(T value, long expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }
    }

    private void evict(String siteCode) {
        onsiteCache.remove(siteCode);
        latestCache.remove(siteCode);
    }

    /**
     * Cleanup ข้อมูลเก่า (เรียกจาก Scheduler)
     */
    @Transactional
    public void cleanupOldJobStatus(int daysToKeep) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysToKeep);
        ttsmJobStatusRepository.deleteByReceivedAtBefore(cutoffDate);
        onsiteCache.clear();
        latestCache.clear();
        log.info("Cleaned up TTSM job status older than {} days", daysToKeep);
    }
}
