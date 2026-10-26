package dev.niceone.smartsiteappserver.controller;

import dev.niceone.smartsiteappserver.model.OnlineOfflineSummaryDaily;
import dev.niceone.smartsiteappserver.model.OnlineStatusDetailResponse;
import dev.niceone.smartsiteappserver.model.OnlineStatusResponse;
import dev.niceone.smartsiteappserver.repository.OnlineStatusRepositoryCustom;
import dev.niceone.smartsiteappserver.service.OnlineOfflineSummaryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.sql.Date;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/summary")
public class SummaryController {
    @Autowired
    private OnlineStatusRepositoryCustom repository;

    @Autowired
    private OnlineOfflineSummaryService onlineOfflineSummaryService;

    @PersistenceContext
    private EntityManager em;

    @GetMapping("/online-offline-summary")
    public Page<OnlineStatusResponse> getStatus(
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) String regionCode,
            @RequestParam(required = false) String provinceCode,
            @RequestParam(required = false) Boolean onlineStatus,
            @RequestParam(required = false) LocalDateTime start,
            @RequestParam(required = false) LocalDateTime end,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return repository.findOnlineStatuses(siteCode, regionCode, provinceCode, onlineStatus, start, end, PageRequest.of(page, size));
    }

    @GetMapping("/daily-summary")
    public List<OnlineOfflineSummaryDaily> getDailySummary(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end
    ) {
        LocalDateTime startDt = LocalDateTime.parse(start);
        LocalDateTime endDt = LocalDateTime.parse(end);
        List<OnlineOfflineSummaryDaily> results = onlineOfflineSummaryService.getDailySummary(startDt,endDt);
        return results;
    }

    @GetMapping("/details-by-date")
    public ResponseEntity<List<OnlineStatusResponse>> getDetailsByDate(
            @RequestParam String todayDate,
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) String regionCode,
            @RequestParam(required = false) String provinceCode,
            @RequestParam(required = false) Boolean onlineStatus
    ) {
        List<OnlineStatusResponse> results = onlineOfflineSummaryService.getOnlineStatusesByDate(
                todayDate,
                siteCode,
                regionCode,
                provinceCode,
                onlineStatus
        );

        return ResponseEntity.ok(results);
    }


    @GetMapping("/details-by-date-paged")
    public Page<OnlineStatusResponse> getDetailsByDatePaged(
            @RequestParam String todayDate,
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) String regionCode,
            @RequestParam(required = false) String provinceCode,
            @RequestParam(required = false) Boolean onlineStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return onlineOfflineSummaryService.getOnlineStatusesByDatePaged(
                todayDate, siteCode, regionCode, provinceCode, onlineStatus, PageRequest.of(page, size)
        );
    }

    @GetMapping("/details-check-by-date")
    public ResponseEntity<List<OnlineStatusDetailResponse>> getDetailsCheckByDate(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) String regionCode,
            @RequestParam(required = false) String provinceCode,
            @RequestParam(required = false) Boolean onlineStatus
    ) {
        List<OnlineStatusDetailResponse> results = onlineOfflineSummaryService.getOnlineOfflineDetailStatusesByDate(
                startDate,
                endDate,
                siteCode,
                regionCode,
                provinceCode,
                onlineStatus
        );

        return ResponseEntity.ok(results);
    }

    @GetMapping("/details-check-by-date-paged")
    public Page<OnlineStatusDetailResponse> getDetailsCheckByDatePaged(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) String regionCode,
            @RequestParam(required = false) String provinceCode,
            @RequestParam(required = false) Boolean onlineStatus,
            @RequestParam(required = false) String pingIpStatus,
            @RequestParam(required = false) String pingGwStatus,
            @RequestParam(required = false) Boolean healthCheckOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return onlineOfflineSummaryService.getOnlineOfflineDetailStatusesByDatePaged(
                startDate, endDate, siteCode, regionCode, provinceCode,
                onlineStatus, pingIpStatus, pingGwStatus, healthCheckOnly, PageRequest.of(page, size)
        );
    }

    @GetMapping("/site-device-type")
    public Page<Map<String, Object>> getSiteDeviceType(
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) String regionCode,
            @RequestParam(required = false) String provinceCode,
            @RequestParam(required = false) String deviceType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> params = new HashMap<>();

        sql.append("SELECT s.id, s.code, s.name_th, r.name AS region, p.name_th AS province, ")
                .append("s.ip_address, ")
                .append("COALESCE(sv.version_name, 'Not Configured') AS device_type ")
                .append("FROM smartsite.site s ")
                .append("JOIN smartsite.region r ON s.region_id = r.id ")
                .append("JOIN smartsite.province p ON s.province_id = p.id ")
                .append("LEFT JOIN smartsite.site_version sv ON sv.site_id = s.id AND sv.active = true ")
                .append("WHERE s.active = true ");

        if (siteCode != null && !siteCode.isEmpty()) {
            sql.append(" AND s.code ILIKE :siteCode ");
            params.put("siteCode", "%" + siteCode + "%");
        }
        if (regionCode != null && !regionCode.isEmpty()) {
            sql.append(" AND r.code ILIKE :regionCode ");
            params.put("regionCode", "%" + regionCode + "%");
        }
        if (provinceCode != null && !provinceCode.isEmpty()) {
            sql.append(" AND p.name_th ILIKE :provinceCode ");
            params.put("provinceCode", "%" + provinceCode + "%");
        }
        if (deviceType != null && !deviceType.isEmpty()) {
            if ("notconfigured".equalsIgnoreCase(deviceType)) {
                sql.append(" AND sv.version_name IS NULL ");
            } else {
                sql.append(" AND sv.version_name = :deviceType ");
                params.put("deviceType", deviceType);
            }
        }

        sql.append(" ORDER BY s.code ASC");

        // Count
        String countSql = "SELECT COUNT(*) FROM (" + sql + ") AS total";
        Query countQuery = em.createNativeQuery(countSql);
        params.forEach(countQuery::setParameter);
        long totalCount = ((Number) countQuery.getSingleResult()).longValue();

        // Data
        Query query = em.createNativeQuery(sql.toString());
        params.forEach(query::setParameter);
        Pageable pageable = PageRequest.of(page, size);
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());

        List<Object[]> results = query.getResultList();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Object[] row : results) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row[0] != null ? ((Number) row[0]).longValue() : null);
            item.put("code", row[1]);
            item.put("nameTh", row[2]);
            item.put("region", row[3]);
            item.put("province", row[4]);
            item.put("ipAddress", row[5]);
            item.put("deviceType", row[6]);
            list.add(item);
        }

        return new PageImpl<>(list, pageable, totalCount);
    }

    // ─── CSV Export endpoints ────────────────────────────────────────────────

    private String escapeCsv(Object val) {
        if (val == null) return "";
        String s = val.toString();
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private ResponseEntity<byte[]> csvResponse(String csvContent, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, "application/octet-stream; charset=UTF-8");
        headers.setContentDispositionFormData("attachment", filename);
        return new ResponseEntity<>(csvContent.getBytes(), headers, HttpStatus.OK);
    }

    @GetMapping("/csv/details-by-date")
    public ResponseEntity<byte[]> exportDetailsByDate(
            @RequestParam String todayDate,
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) String regionCode,
            @RequestParam(required = false) String provinceCode,
            @RequestParam(required = false) Boolean onlineStatus
    ) {
        List<OnlineStatusResponse> rows = onlineOfflineSummaryService.getOnlineStatusesByDate(
                todayDate, siteCode, regionCode, provinceCode, onlineStatus
        );
        StringBuilder csv = new StringBuilder();
        csv.append('\uFEFF');
        csv.append("Site Code,Region,Province,IP Address,Gateway,Last Update,Status\n");
        for (OnlineStatusResponse r : rows) {
            csv.append(escapeCsv(r.getCode())).append(",")
               .append(escapeCsv(r.getRegion())).append(",")
               .append(escapeCsv(r.getProvince())).append(",")
               .append(escapeCsv(r.getIpAddress())).append(",")
               .append(escapeCsv(r.getDefaultGateway())).append(",")
               .append(escapeCsv(r.getCheckTime())).append(",")
               .append(r.getOnlineStatus() != null && r.getOnlineStatus() ? "Online" : "Offline")
               .append("\n");
        }
        return csvResponse(csv.toString(), "OnlineOfflineDetails_" + todayDate + ".csv");
    }

    @GetMapping("/csv/details-check-by-date")
    public ResponseEntity<byte[]> exportDetailsCheckByDate(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) String regionCode,
            @RequestParam(required = false) String provinceCode,
            @RequestParam(required = false) Boolean onlineStatus
    ) {
        List<OnlineStatusDetailResponse> rows = onlineOfflineSummaryService.getOnlineOfflineDetailStatusesByDate(
                startDate, endDate, siteCode, regionCode, provinceCode, onlineStatus
        );
        StringBuilder csv = new StringBuilder();
        csv.append('\uFEFF');
        csv.append("Site Code,Region,Province,IP Address,Gateway,Last Check,Ping IP,Ping GW\n");
        for (OnlineStatusDetailResponse r : rows) {
            csv.append(escapeCsv(r.getCode())).append(",")
               .append(escapeCsv(r.getRegion())).append(",")
               .append(escapeCsv(r.getProvince())).append(",")
               .append(escapeCsv(r.getIpAddress())).append(",")
               .append(escapeCsv(r.getDefaultGateway())).append(",")
               .append(escapeCsv(r.getCheckTime())).append(",")
               .append(r.getIsPingIp() == null ? "Wait" : (r.getIsPingIp() ? "Success" : "Fail")).append(",")
               .append(r.getIsPingGateway() == null ? "Wait" : (r.getIsPingGateway() ? "Success" : "Fail"))
               .append("\n");
        }
        return csvResponse(csv.toString(), "OfflineCheckup_" + startDate + "_" + endDate + ".csv");
    }

    @GetMapping("/csv/healthcheck-daily")
    public ResponseEntity<byte[]> exportHealthCheckDaily(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) String regionCode,
            @RequestParam(required = false) String provinceCode
    ) {
        List<OnlineStatusDetailResponse> rows = onlineOfflineSummaryService.getOnlineOfflineDetailStatusesByDate(
                startDate, endDate, siteCode, regionCode, provinceCode, true
        );
        StringBuilder csv = new StringBuilder();
        csv.append('\uFEFF');
        csv.append("Site Code,Region,Province,IP Address,Last Check,Health Check,Action\n");
        for (OnlineStatusDetailResponse r : rows) {
            csv.append(escapeCsv(r.getCode())).append(",")
               .append(escapeCsv(r.getRegion())).append(",")
               .append(escapeCsv(r.getProvince())).append(",")
               .append(escapeCsv(r.getIpAddress())).append(",")
               .append(escapeCsv(r.getCheckTime())).append(",")
               .append(r.getIsCallHelpcheck() == null ? "Wait" : (r.getIsCallHelpcheck() ? "Success" : "Fail")).append(",")
               .append(r.getStatusCallHelpcheck() == null ? "Wait" : (r.getStatusCallHelpcheck() ? "Pass" : "Fail"))
               .append("\n");
        }
        return csvResponse(csv.toString(), "HealthCheckDaily_" + startDate + "_" + endDate + ".csv");
    }

    @GetMapping("/csv/site-device-type")
    public ResponseEntity<byte[]> exportSiteDeviceType(
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) String regionCode,
            @RequestParam(required = false) String provinceCode,
            @RequestParam(required = false) String deviceType
    ) {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> params = new HashMap<>();

        sql.append("SELECT s.code, s.name_th, r.name AS region, p.name_th AS province, ")
                .append("s.ip_address, ")
                .append("COALESCE(sv.version_name, 'Not Configured') AS device_type ")
                .append("FROM smartsite.site s ")
                .append("JOIN smartsite.region r ON s.region_id = r.id ")
                .append("JOIN smartsite.province p ON s.province_id = p.id ")
                .append("LEFT JOIN smartsite.site_version sv ON sv.site_id = s.id AND sv.active = true ")
                .append("WHERE s.active = true ");

        if (siteCode != null && !siteCode.isEmpty()) {
            sql.append(" AND s.code ILIKE :siteCode ");
            params.put("siteCode", "%" + siteCode + "%");
        }
        if (regionCode != null && !regionCode.isEmpty()) {
            sql.append(" AND r.code ILIKE :regionCode ");
            params.put("regionCode", "%" + regionCode + "%");
        }
        if (provinceCode != null && !provinceCode.isEmpty()) {
            sql.append(" AND p.name_th ILIKE :provinceCode ");
            params.put("provinceCode", "%" + provinceCode + "%");
        }
        if (deviceType != null && !deviceType.isEmpty()) {
            if ("notconfigured".equalsIgnoreCase(deviceType)) {
                sql.append(" AND sv.version_name IS NULL ");
            } else {
                sql.append(" AND sv.version_name = :deviceType ");
                params.put("deviceType", deviceType);
            }
        }
        sql.append(" ORDER BY s.code ASC");

        Query query = em.createNativeQuery(sql.toString());
        params.forEach(query::setParameter);

        List<Object[]> results = query.getResultList();
        StringBuilder csv = new StringBuilder();
        csv.append('\uFEFF');
        csv.append("Site Code,Site Name,Region,Province,IP Address,Device Type\n");
        for (Object[] row : results) {
            csv.append(escapeCsv(row[0])).append(",")
               .append(escapeCsv(row[1])).append(",")
               .append(escapeCsv(row[2])).append(",")
               .append(escapeCsv(row[3])).append(",")
               .append(escapeCsv(row[4])).append(",")
               .append(escapeCsv(row[5]))
               .append("\n");
        }
        return csvResponse(csv.toString(), "SiteDeviceType.csv");
    }
}
