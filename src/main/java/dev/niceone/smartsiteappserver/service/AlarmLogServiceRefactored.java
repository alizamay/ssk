package dev.niceone.smartsiteappserver.service;

import dev.niceone.smartsiteappserver.model.TtsmJobStatus;
import dev.niceone.smartsiteappserver.payload.request.SiteStatusRequest;
import dev.niceone.smartsiteappserver.payload.response.OnsiteResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/**
 * ตัวอย่างการแก้ไข AlarmLogService
 * เปลี่ยนจาก askTTSMOnSite (เรียก TTSM API)
 * เป็น อ่านจาก database ที่ TTSM ส่งมาให้
 *
 * jobStatus จาก TTSM:
 *   "On-Site" → ช่างอยู่ที่ site (isOnsite = 1)
 *   "Done"    → ช่างออกจาก site แล้ว (isOnsite = 2)
 *   "Held"    → งานถูก hold (isOnsite = 0)
 */
@Service
@Slf4j
public class AlarmLogServiceRefactored {

    @Autowired
    private TtsmWebhookService ttsmWebhookService;

    /**
     * แทนที่: askTTSMOnSite (AlarmLogService.java:388)
     *
     * เดิม: POST ไปหา TTSM API
     * ใหม่: อ่านจาก database
     */
    public ResponseEntity<OnsiteResponse> checkTechnicianOnsite(SiteStatusRequest siteStatusRequest) {
        String siteCode = siteStatusRequest.getSite_code();

        log.info("Checking technician onsite status for site: {}", siteCode);

        TtsmJobStatus latestJob = ttsmWebhookService.getLatestJobStatus(siteCode);

        OnsiteResponse response;

        if (latestJob == null) {
            response = new OnsiteResponse(siteCode, 0, "");
            log.warn("No job status found for site: {}", siteCode);

        } else {
            int isOnsite = mapJobStatusToIsOnsite(latestJob.getJobStatus());

            String raw = String.format("Job ID: %s | Status: %s | Received: %s",
                    latestJob.getJobId(),
                    latestJob.getJobStatus(),
                    latestJob.getReceivedAt());

            response = new OnsiteResponse(siteCode, isOnsite, raw);

            log.info("Site {} onsite status: {} | Job: {}",
                    siteCode, latestJob.getJobStatus(), latestJob.getJobId());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * แปลง jobStatus เป็นตัวเลข
     * "On-Site" → 1, "Done" → 2, "Held"/อื่นๆ → 0
     */
    private int mapJobStatusToIsOnsite(String jobStatus) {
        if ("On-Site".equals(jobStatus)) return 1;
        if ("Done".equals(jobStatus)) return 2;
        return 0;
    }

    /**
     * เช็คว่ามีช่างอยู่ onsite หรือไม่ (แบบง่าย)
     */
    public boolean isTechnicianOnsite(String siteCode) {
        return ttsmWebhookService.isTechnicianOnsite(siteCode);
    }
}
