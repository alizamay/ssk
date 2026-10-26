package dev.niceone.smartsiteappserver.controller;

import dev.niceone.smartsiteappserver.dto.TtsmJobStatusDto;
import dev.niceone.smartsiteappserver.model.TtsmJobStatus;
import dev.niceone.smartsiteappserver.payload.ApiResponse;
import dev.niceone.smartsiteappserver.repository.SiteRepository;
import dev.niceone.smartsiteappserver.repository.TtsmJobStatusRepository;
import dev.niceone.smartsiteappserver.service.OnsiteCheckLogService;
import dev.niceone.smartsiteappserver.service.TtsmWebhookService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller สำหรับรับ webhook จาก TTSM
 */
@RestController
@RequestMapping("/api/ttsm/webhook")
@Slf4j
public class TtsmWebhookController {

    @Autowired
    private TtsmWebhookService ttsmWebhookService;

    @Autowired
    private OnsiteCheckLogService onsiteCheckLogService;

    @Autowired
    private TtsmJobStatusRepository ttsmJobStatusRepository;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private Environment env;

    @PersistenceContext
    private EntityManager em;

    /**
     * ดึงรายการ site ที่สถานะล่าสุดเป็น On-Site
     * GET /api/ttsm/webhook/onsite-list
     */
    @GetMapping("/onsite-list")
    public ResponseEntity<?> getOnsiteList() {
        List<TtsmJobStatus> list = ttsmJobStatusRepository.findAllLatestOnSite();
        return ResponseEntity.ok(list);
    }

    /**
     * ดึงสถานะล่าสุดของทุก site พร้อม filter
     * GET /api/ttsm/webhook/list?jobStatus=On-Site&keyword=&pageNumber=0&pageSize=10
     */
    @GetMapping("/list")
    public ResponseEntity<?> getList(
            @RequestParam(name = "jobStatus", required = false) String jobStatus,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(value = "pageNumber", defaultValue = "0") int pageNumber,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        String status = (jobStatus != null && !jobStatus.isEmpty()) ? jobStatus : null;
        String kw = (keyword != null && !keyword.isEmpty()) ? keyword : null;
        Page<TtsmJobStatus> page = ttsmJobStatusRepository.findAllLatestGrouped(status, kw, PageRequest.of(pageNumber, pageSize));
        return ResponseEntity.ok(page);
    }

    private String buildHistoryWhere(String status, String kw, LocalDateTime sdLdt, LocalDateTime edLdt) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        if (status != null) where.append(" AND t.job_status = :jobStatus");
        if (kw != null) where.append(" AND (t.site_code LIKE :kw OR t.job_id LIKE :kw)");
        if (sdLdt != null) where.append(" AND t.action_date >= :startDate");
        if (edLdt != null) where.append(" AND t.action_date <= :endDate");
        return where.toString();
    }

    private void setHistoryParams(Query q, String status, String kw, LocalDateTime sdLdt, LocalDateTime edLdt) {
        if (status != null) q.setParameter("jobStatus", status);
        if (kw != null) q.setParameter("kw", "%" + kw + "%");
        if (sdLdt != null) q.setParameter("startDate", sdLdt);
        if (edLdt != null) q.setParameter("endDate", edLdt);
    }

    /**
     * ดึงประวัติ TTSM ทั้งหมด (ไม่ group by site)
     */
    @GetMapping("/history")
    public ResponseEntity<?> getHistory(
            @RequestParam(name = "jobStatus", required = false) String jobStatus,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "startDate", required = false) String startDate,
            @RequestParam(name = "endDate", required = false) String endDate,
            @RequestParam(value = "pageNumber", defaultValue = "0") int pageNumber,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        String status = (jobStatus != null && !jobStatus.isEmpty()) ? jobStatus : null;
        String kw = (keyword != null && !keyword.isEmpty()) ? keyword : null;
        LocalDateTime sdLdt = null;
        LocalDateTime edLdt = null;
        if (startDate != null && !startDate.isEmpty()) {
            sdLdt = LocalDate.parse(startDate, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
        }
        if (endDate != null && !endDate.isEmpty()) {
            edLdt = LocalDate.parse(endDate, DateTimeFormatter.ISO_LOCAL_DATE).atTime(23, 59, 59);
        }

        String whereClause = buildHistoryWhere(status, kw, sdLdt, edLdt);

        // count
        Query cq = em.createNativeQuery("SELECT COUNT(*) FROM smartsite.ttsm_job_status t" + whereClause);
        setHistoryParams(cq, status, kw, sdLdt, edLdt);
        long total = ((Number) cq.getSingleResult()).longValue();

        // data
        Query dq = em.createNativeQuery("SELECT t.id, t.site_code, t.job_id, t.job_status, t.action_date, t.received_at FROM smartsite.ttsm_job_status t" + whereClause + " ORDER BY t.received_at DESC");
        setHistoryParams(dq, status, kw, sdLdt, edLdt);
        Pageable pageable = PageRequest.of(pageNumber, pageSize);
        dq.setFirstResult((int) pageable.getOffset());
        dq.setMaxResults(pageSize);

        List<Object[]> rows = dq.getResultList();
        List<Map<String, Object>> result = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (Object[] r : rows) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", r[0]);
            map.put("siteCode", r[1]);
            map.put("jobId", r[2]);
            map.put("jobStatus", r[3]);
            map.put("actionDate", r[4] != null ? r[4].toString() : null);
            map.put("receivedAt", r[5] != null ? r[5].toString() : null);
            result.add(map);
        }
        return ResponseEntity.ok(new PageImpl<>(result, pageable, total));
    }

    /**
     * Export TTSM history to CSV
     */
    @GetMapping("/history/csv")
    public void exportHistoryCsv(
            @RequestParam(name = "jobStatus", required = false) String jobStatus,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "startDate", required = false) String startDate,
            @RequestParam(name = "endDate", required = false) String endDate,
            javax.servlet.http.HttpServletResponse response) throws Exception {
        String status = (jobStatus != null && !jobStatus.isEmpty()) ? jobStatus : null;
        String kw = (keyword != null && !keyword.isEmpty()) ? keyword : null;
        LocalDateTime sdLdt = null;
        LocalDateTime edLdt = null;
        if (startDate != null && !startDate.isEmpty()) {
            sdLdt = LocalDate.parse(startDate, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
        }
        if (endDate != null && !endDate.isEmpty()) {
            edLdt = LocalDate.parse(endDate, DateTimeFormatter.ISO_LOCAL_DATE).atTime(23, 59, 59);
        }

        String whereClause = buildHistoryWhere(status, kw, sdLdt, edLdt);
        Query dq = em.createNativeQuery("SELECT t.site_code, t.job_id, t.job_status, t.action_date, t.received_at FROM smartsite.ttsm_job_status t" + whereClause + " ORDER BY t.received_at DESC");
        setHistoryParams(dq, status, kw, sdLdt, edLdt);
        List<Object[]> rows = dq.getResultList();

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=TtsmHistory.csv");
        java.io.PrintWriter writer = response.getWriter();
        writer.write('\ufeff');
        writer.println("Site Code,Job ID,Status,Action Date,Received At");
        for (Object[] r : rows) {
            writer.println(String.join(",",
                    r[0] != null ? r[0].toString() : "",
                    r[1] != null ? r[1].toString() : "",
                    r[2] != null ? r[2].toString() : "",
                    r[3] != null ? r[3].toString() : "",
                    r[4] != null ? r[4].toString() : ""
            ));
        }
        writer.flush();
    }

    /**
     * TTSM ส่ง job status มาให้
     * POST /api/ttsm/webhook/job-status
     *
     * Body: { "siteCode": ["BKKYM","100CP"], "jobId": "JB26-000001",
     *         "jobStatus": "On-Site|Done|Held", "actionDate": "yyyy-MM-dd HH:mm:ss" }
     */
    @PostMapping("/job-status")
    public ResponseEntity<?> receiveJobStatus(
            @RequestBody TtsmJobStatusDto jobStatus,
            @RequestHeader(value = "apiKey", required = false) String apiKey) {

        if (!validateApiKey(apiKey)) {
            log.warn("Invalid API key from TTSM");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(false, "Invalid API Key"));
        }

        try {
            // Validate: check ว่า siteCode ที่ส่งมามีอยู่ในระบบหรือไม่
            if (jobStatus.getSiteCode() == null || jobStatus.getSiteCode().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "siteCode is required"));
            }

            List<String> invalidCodes = new ArrayList<>();
            for (String code : jobStatus.getSiteCode()) {
                if (!siteRepository.findByCode(code).isPresent()) {
                    invalidCodes.add(code);
                }
            }

            if (!invalidCodes.isEmpty()) {
                log.warn("TTSM sent invalid site codes: {}", invalidCodes);
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "Site codes not found: " + invalidCodes));
            }

            ttsmWebhookService.saveJobStatus(jobStatus);
            log.info("Received job status from TTSM | Job: {} | Status: {} | Sites: {}",
                    jobStatus.getJobId(), jobStatus.getJobStatus(), jobStatus.getSiteCode());
            return ResponseEntity.ok(new ApiResponse(true, "Job status received"));

        } catch (Exception e) {
            log.error("Error saving job status from TTSM: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Error: " + e.getMessage()));
        }
    }

    /**
     * ดูประวัติที่ PI/Android call มาเช็ค onsite
     * GET /api/ttsm/webhook/check-log?siteCode=BKKYM&startDate=2026-04-01&endDate=2026-04-30&responseStatus=1&pageNumber=0&pageSize=20
     */
    @GetMapping("/check-log")
    public ResponseEntity<?> getCheckLog(
            @RequestParam(name = "siteCode", required = false) String siteCode,
            @RequestParam(name = "startDate", required = false) String startDate,
            @RequestParam(name = "endDate", required = false) String endDate,
            @RequestParam(name = "responseStatus", required = false) Integer responseStatus,
            @RequestParam(value = "pageNumber", defaultValue = "0") int pageNumber,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {

        String sc = (siteCode != null && !siteCode.isEmpty()) ? siteCode : null;
        LocalDateTime sdLdt = parseStartDate(startDate);
        LocalDateTime edLdt = parseEndDate(endDate);

        return ResponseEntity.ok(
                onsiteCheckLogService.findByFilters(sc, sdLdt, edLdt, responseStatus, PageRequest.of(pageNumber, pageSize))
        );
    }

    /**
     * Export onsite check log to CSV
     * GET /api/ttsm/webhook/check-log/csv
     */
    @GetMapping("/check-log/csv")
    public void exportCheckLogCsv(
            @RequestParam(name = "siteCode", required = false) String siteCode,
            @RequestParam(name = "startDate", required = false) String startDate,
            @RequestParam(name = "endDate", required = false) String endDate,
            @RequestParam(name = "responseStatus", required = false) Integer responseStatus,
            javax.servlet.http.HttpServletResponse response) throws Exception {

        String sc = (siteCode != null && !siteCode.isEmpty()) ? siteCode : null;
        LocalDateTime sdLdt = parseStartDate(startDate);
        LocalDateTime edLdt = parseEndDate(endDate);

        List<dev.niceone.smartsiteappserver.model.OnsiteCheckLog> rows =
                onsiteCheckLogService.findAllByFilters(sc, sdLdt, edLdt, responseStatus);

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=OnsiteCheckLog.csv");
        java.io.PrintWriter writer = response.getWriter();
        writer.write('﻿');
        writer.println("Request Time,Site Code,Client IP,Result,Response");
        for (dev.niceone.smartsiteappserver.model.OnsiteCheckLog r : rows) {
            String resultLabel = r.getResponseStatus() == 1 ? "Found"
                    : r.getResponseStatus() == 0 ? "Not Found" : "Error";
            String raw = r.getResponseRaw() != null ? r.getResponseRaw().replace(",", ";").replace("\n", " ") : "";
            writer.println(String.join(",",
                    r.getRequestTime() != null ? r.getRequestTime().toString() : "",
                    r.getSiteCode() != null ? r.getSiteCode() : "",
                    r.getClientIp() != null ? r.getClientIp() : "",
                    resultLabel,
                    raw
            ));
        }
        writer.flush();
    }

    private LocalDateTime parseStartDate(String startDate) {
        if (startDate == null || startDate.isEmpty()) return null;
        return LocalDate.parse(startDate, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
    }

    private LocalDateTime parseEndDate(String endDate) {
        if (endDate == null || endDate.isEmpty()) return null;
        return LocalDate.parse(endDate, DateTimeFormatter.ISO_LOCAL_DATE).atTime(23, 59, 59);
    }

    private boolean validateApiKey(String apiKey) {
        String expectedKey = env.getProperty("apikey_by_ttsm");
        if (expectedKey == null || apiKey == null) {
            return false;
        }
        return expectedKey.equals(apiKey);
    }
}
