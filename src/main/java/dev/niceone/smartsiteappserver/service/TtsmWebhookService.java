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
            log.info("Saved job status from TTSM | Site: {} | Job: {} | Status: {}",
                    siteCode, dto.getJobId(), dto.getJobStatus());
        });
    }

    /**
     * เช็คว่ามีช่างอยู่ onsite หรือไม่
     * Logic: มี jobId ของ site นี้ที่ latest status ยังเป็น "On-Site" อยู่ (ยังไม่ Done/Held)
     */
    public boolean isTechnicianOnsite(String siteCode) {
        return ttsmJobStatusRepository.existsActiveOnsiteJob(siteCode);
    }

    /**
     * ดึง Job Status ล่าสุดของ Site
     */
    public TtsmJobStatus getLatestJobStatus(String siteCode) {
        return ttsmJobStatusRepository
                .findFirstBySiteCodeOrderByReceivedAtDesc(siteCode)
                .orElse(null);
    }

    /**
     * Cleanup ข้อมูลเก่า (เรียกจาก Scheduler)
     */
    @Transactional
    public void cleanupOldJobStatus(int daysToKeep) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysToKeep);
        ttsmJobStatusRepository.deleteByReceivedAtBefore(cutoffDate);
        log.info("Cleaned up TTSM job status older than {} days", daysToKeep);
    }
}
