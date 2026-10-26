package dev.niceone.smartsiteappserver.repository;

import dev.niceone.smartsiteappserver.model.OnlineOfflineSummaryDaily;
import dev.niceone.smartsiteappserver.model.OnlineStatusDetailResponse;
import dev.niceone.smartsiteappserver.model.OnlineStatusResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class OnlineStatusRepositoryImpl implements OnlineStatusRepositoryCustom {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Page<OnlineStatusResponse> findOnlineStatuses(String siteCode, String regionCode, String provinceCode, Boolean onlineStatus,
                                                         LocalDateTime start, LocalDateTime end, Pageable pageable) {

        StringBuilder sql = new StringBuilder();
        sql.append("select site.id, site.code, site.name_th, site.name_en, site.ip_address, site.default_gateway, site.subnet_mask, ")
                .append("site.port, site.site_key, site.status, site.active, site.is_install, site.installed_date, site.lat, site.lon, ")
                .append("site.contact_point_name, site.contact_point_tel, site.address, site.key_generating, site.last_key_generate, ")
                .append("site.last_key_update, site.report_status_at, site.emerg_key_success, site.version, site.motion, ")
                .append("site.firmware_option, region.name as region, province.name_th as province, log.check_time, log.status as online ")
                .append("from smartsite.site site ")
                .append("join smartsite.region region on site.region_id = region.id ")
                .append("join smartsite.province province on site.province_id = province.id ")
                .append("join smartsite.site_online_status_log log on site.code = log.site_code ")
                .append("where site.active = true ");

        Map<String, Object> params = new HashMap<>();

        if (siteCode != null && !siteCode.isEmpty()) {
            sql.append(" and site.code ilike :siteCode ");
            params.put("siteCode", "%" + siteCode + "%");
        }
        if (regionCode != null) {
            sql.append(" and region.id = :regionCode ");
            params.put("regionCode", regionCode);
        }
        if (provinceCode != null) {
            sql.append(" and province.id = :provinceCode ");
            params.put("provinceCode", provinceCode);
        }
        if (onlineStatus != null) {
            sql.append(" and log.status = :onlineStatus ");
            params.put("onlineStatus", onlineStatus);
        }
        if (start != null && end != null) {
            sql.append(" and log.check_time between :start and :end ");
            params.put("start", start);
            params.put("end", end);
        }

        sql.append(" order by log.check_time desc ");

        Query query = em.createNativeQuery(sql.toString(), "OnlineStatusResponseMapping");
        Query countQuery = em.createNativeQuery("select count(*) from (" + sql.toString() + ") as total");

        params.forEach((k, v) -> {
            query.setParameter(k, v);
            countQuery.setParameter(k, v);
        });

        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());

        List<OnlineStatusResponse> resultList = query.getResultList();
        Number count = (Number) countQuery.getSingleResult();

        return new PageImpl<>(resultList, pageable, count.longValue());
    }

    @Override
    public List<OnlineOfflineSummaryDaily> getDailySummaryCounts(LocalDateTime start, LocalDateTime end) {
        String sql = "SELECT DATE(sosl.check_time) AS summary_date, " +
                "SUM(CASE WHEN sosl.status = true THEN 1 ELSE 0 END) AS online_count, " +
                "SUM(CASE WHEN sosl.status = false THEN 1 ELSE 0 END) AS offline_count " +
                "FROM smartsite.site_online_status_log sosl " +
                "WHERE (CAST(:start AS timestamp) IS NULL OR sosl.check_time >= CAST(:start AS timestamp)) " +
                "AND (CAST(:end AS timestamp) IS NULL OR sosl.check_time <= CAST(:end AS timestamp)) " +
                "GROUP BY DATE(sosl.check_time) " +
                "ORDER BY summary_date DESC";

        Query query = em.createNativeQuery(sql);
        query.setParameter("start", start);
        query.setParameter("end", end);

        List<Object[]> results = query.getResultList();
        List<OnlineOfflineSummaryDaily> summaries = new ArrayList<>();

        for (Object[] row : results) {
            OnlineOfflineSummaryDaily summary = new OnlineOfflineSummaryDaily();
            summary.setDate(row[0] != null ? ((java.sql.Date) row[0]).toLocalDate() : null);
            summary.setOnlineCount(((Number) row[1]).intValue());
            summary.setOfflineCount(((Number) row[2]).intValue());
            summaries.add(summary);
        }

        return summaries;
    }

    @Override
    public List<OnlineStatusResponse> findTodayOnlineStatuses(
            String todayDate,
            String siteCode,
            String regionCode,
            String provinceCode,
            Boolean onlineStatus
    ) {
        // แปลง todayDate ('YYYY-MM-DD') เป็นช่วงเวลา LocalDateTime
        LocalDate date = LocalDate.parse(todayDate);
        LocalDateTime startOfDay = date.atStartOfDay();                // 00:00:00
        LocalDateTime endOfDay = date.atTime(23, 59, 59);              // 23:59:59

        StringBuilder sql = new StringBuilder();
        Map<String, Object> params = new HashMap<>();

        sql.append("select site.id, site.code, site.name_th, site.name_en, site.ip_address, site.default_gateway, site.subnet_mask, ")
                .append("site.port, site.site_key, site.status, site.active, site.is_install, site.installed_date, site.lat, site.lon, ")
                .append("site.contact_point_name, site.contact_point_tel, site.address, site.key_generating, site.last_key_generate, ")
                .append("site.last_key_update, site.report_status_at, site.emerg_key_success, site.version, site.motion, ")
                .append("site.firmware_option, region.name as region, province.name_th as province, log.check_time, log.status as online ")
                .append("from smartsite.site site ")
                .append("join smartsite.region region on site.region_id = region.id ")
                .append("join smartsite.province province on site.province_id = province.id ")
                .append("join smartsite.site_online_status_log log on site.code = log.site_code ")
                .append("where log.check_time between :start and :end ");

        params.put("start", startOfDay);
        params.put("end", endOfDay);

        if (siteCode != null && !siteCode.isEmpty()) {
            sql.append(" and site.code ilike :siteCode ");
            params.put("siteCode", "%" + siteCode + "%");
        }

        // ตรวจสอบ regionCode (ตอนนี้ควรเป็น String)
        if (regionCode != null && !regionCode.isEmpty()) {
            // เปลี่ยนจาก region.id = เป็น region.code ilike
            sql.append(" and region.code ilike :regionCode ");
            // เพิ่ม wildcard '%' สำหรับการค้นหาแบบ partial match
            params.put("regionCode", "%" + regionCode + "%");
        }

// ตรวจสอบ provinceCode (ตอนนี้ควรเป็น String)
        if (provinceCode != null && !provinceCode.isEmpty()) {
            // เปลี่ยนจาก province.id = เป็น province.code ilike
            sql.append(" and province.name_th ilike :provinceCode ");
            // เพิ่ม wildcard '%'
            params.put("provinceCode", "%" + provinceCode + "%");
        }

        if (onlineStatus != null) {
            sql.append(" and log.status = :onlineStatus ");
            params.put("onlineStatus", onlineStatus);
        }

        sql.append(" order by log.check_time desc");



        Query query = em.createNativeQuery(sql.toString());

        // set parameters
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            query.setParameter(entry.getKey(), entry.getValue());
        }

        List<Object[]> results = query.getResultList();
        List<OnlineStatusResponse> todayDetail = new ArrayList<>();

        for (Object[] row : results) {
            OnlineStatusResponse onlineOffline = new OnlineStatusResponse();

            // การแปลงค่าจะขึ้นอยู่กับชนิดข้อมูลที่ได้จาก Database Driver
            // โดยทั่วไป ตัวเลขจะเป็น BigInteger/BigDecimal, วันที่จะเป็น Timestamp
            onlineOffline.setId(row[0] != null ? ((Number) row[0]).longValue() : null);
            onlineOffline.setCode((String) row[1]);
            onlineOffline.setNameTh((String) row[2]);
            onlineOffline.setNameEn((String) row[3]);
            onlineOffline.setIpAddress((String) row[4]);
            onlineOffline.setDefaultGateway((String) row[5]);
            onlineOffline.setSubnetMask((String) row[6]);
            onlineOffline.setPort(row[7] != null ? ((Number) row[7]).intValue() : null);
            onlineOffline.setSiteKey((String) row[8]);
            onlineOffline.setStatus(row[9] != null ? ((Number) row[9]).intValue() : null);
            onlineOffline.setActive((Boolean) row[10]);
            onlineOffline.setIsInstall((Boolean) row[11]);
            onlineOffline.setInstalledDate(row[12] != null ? ((java.sql.Timestamp) row[12]).toLocalDateTime() : null);
            onlineOffline.setLat((String) row[13]);
            onlineOffline.setLon((String) row[14]);
            onlineOffline.setContactPointName((String) row[15]);
            onlineOffline.setContactPointTel((String) row[16]);
            onlineOffline.setAddress((String) row[17]);
            onlineOffline.setKeyGenerating((Boolean) row[18]);
            onlineOffline.setLastKeyGenerate(row[19] != null ? ((java.sql.Timestamp) row[19]).toLocalDateTime() : null);
            onlineOffline.setLastKeyUpdate(row[20] != null ? ((java.sql.Timestamp) row[20]).toLocalDateTime() : null);
            onlineOffline.setReportStatusAt(row[21] != null ? ((java.sql.Timestamp) row[21]).toLocalDateTime() : null);
            onlineOffline.setEmergKeySuccess((Boolean) row[22]);
            onlineOffline.setVersion((String) row[23]);
            onlineOffline.setMotion(row[24] != null ? ((Number) row[24]).intValue() : null);
            onlineOffline.setFirmwareOption(row[25] != null ? ((Number) row[25]).intValue() : null);
            onlineOffline.setRegion((String) row[26]);
            onlineOffline.setProvince((String) row[27]);
            onlineOffline.setCheckTime(row[28] != null ? ((java.sql.Timestamp) row[28]).toLocalDateTime() : null);
            onlineOffline.setOnlineStatus((Boolean) row[29]);

            todayDetail.add(onlineOffline);
        }



        return todayDetail;
    }

    @Override
    public Page<OnlineStatusResponse> findTodayOnlineStatusesPaged(
            String todayDate,
            String siteCode,
            String regionCode,
            String provinceCode,
            Boolean onlineStatus,
            Pageable pageable
    ) {
        LocalDate date = LocalDate.parse(todayDate);
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(23, 59, 59);

        StringBuilder sql = new StringBuilder();
        Map<String, Object> params = new HashMap<>();

        sql.append("select site.id, site.code, site.name_th, site.name_en, site.ip_address, site.default_gateway, site.subnet_mask, ")
                .append("site.port, site.site_key, site.status, site.active, site.is_install, site.installed_date, site.lat, site.lon, ")
                .append("site.contact_point_name, site.contact_point_tel, site.address, site.key_generating, site.last_key_generate, ")
                .append("site.last_key_update, site.report_status_at, site.emerg_key_success, site.version, site.motion, ")
                .append("site.firmware_option, region.name as region, province.name_th as province, log.check_time, log.status as online ")
                .append("from smartsite.site site ")
                .append("join smartsite.region region on site.region_id = region.id ")
                .append("join smartsite.province province on site.province_id = province.id ")
                .append("join smartsite.site_online_status_log log on site.code = log.site_code ")
                .append("where log.check_time between :start and :end ");

        params.put("start", startOfDay);
        params.put("end", endOfDay);

        if (siteCode != null && !siteCode.isEmpty()) {
            sql.append(" and site.code ilike :siteCode ");
            params.put("siteCode", "%" + siteCode + "%");
        }
        if (regionCode != null && !regionCode.isEmpty()) {
            sql.append(" and region.code ilike :regionCode ");
            params.put("regionCode", "%" + regionCode + "%");
        }
        if (provinceCode != null && !provinceCode.isEmpty()) {
            sql.append(" and province.name_th ilike :provinceCode ");
            params.put("provinceCode", "%" + provinceCode + "%");
        }
        if (onlineStatus != null) {
            sql.append(" and log.status = :onlineStatus ");
            params.put("onlineStatus", onlineStatus);
        }

        sql.append(" order by log.check_time desc");

        // Count query
        String countSql = "select count(*) from (" + sql.toString() + ") as total";
        Query countQuery = em.createNativeQuery(countSql);
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            countQuery.setParameter(entry.getKey(), entry.getValue());
        }
        long totalCount = ((Number) countQuery.getSingleResult()).longValue();

        // Data query with pagination
        Query query = em.createNativeQuery(sql.toString());
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            query.setParameter(entry.getKey(), entry.getValue());
        }
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());

        List<Object[]> results = query.getResultList();
        List<OnlineStatusResponse> todayDetail = new ArrayList<>();

        for (Object[] row : results) {
            OnlineStatusResponse onlineOffline = new OnlineStatusResponse();
            onlineOffline.setId(row[0] != null ? ((Number) row[0]).longValue() : null);
            onlineOffline.setCode((String) row[1]);
            onlineOffline.setNameTh((String) row[2]);
            onlineOffline.setNameEn((String) row[3]);
            onlineOffline.setIpAddress((String) row[4]);
            onlineOffline.setDefaultGateway((String) row[5]);
            onlineOffline.setSubnetMask((String) row[6]);
            onlineOffline.setPort(row[7] != null ? ((Number) row[7]).intValue() : null);
            onlineOffline.setSiteKey((String) row[8]);
            onlineOffline.setStatus(row[9] != null ? ((Number) row[9]).intValue() : null);
            onlineOffline.setActive((Boolean) row[10]);
            onlineOffline.setIsInstall((Boolean) row[11]);
            onlineOffline.setInstalledDate(row[12] != null ? ((java.sql.Timestamp) row[12]).toLocalDateTime() : null);
            onlineOffline.setLat((String) row[13]);
            onlineOffline.setLon((String) row[14]);
            onlineOffline.setContactPointName((String) row[15]);
            onlineOffline.setContactPointTel((String) row[16]);
            onlineOffline.setAddress((String) row[17]);
            onlineOffline.setKeyGenerating((Boolean) row[18]);
            onlineOffline.setLastKeyGenerate(row[19] != null ? ((java.sql.Timestamp) row[19]).toLocalDateTime() : null);
            onlineOffline.setLastKeyUpdate(row[20] != null ? ((java.sql.Timestamp) row[20]).toLocalDateTime() : null);
            onlineOffline.setReportStatusAt(row[21] != null ? ((java.sql.Timestamp) row[21]).toLocalDateTime() : null);
            onlineOffline.setEmergKeySuccess((Boolean) row[22]);
            onlineOffline.setVersion((String) row[23]);
            onlineOffline.setMotion(row[24] != null ? ((Number) row[24]).intValue() : null);
            onlineOffline.setFirmwareOption(row[25] != null ? ((Number) row[25]).intValue() : null);
            onlineOffline.setRegion((String) row[26]);
            onlineOffline.setProvince((String) row[27]);
            onlineOffline.setCheckTime(row[28] != null ? ((java.sql.Timestamp) row[28]).toLocalDateTime() : null);
            onlineOffline.setOnlineStatus((Boolean) row[29]);
            todayDetail.add(onlineOffline);
        }

        return new PageImpl<>(todayDetail, pageable, totalCount);
    }

    @Override
    public List<OnlineStatusDetailResponse> findOnlineOfflineCheckDetailStatuses(
            String start,
            String end,
            String siteCode,
            String regionCode,
            String provinceCode,
            Boolean onlineStatus
    ) {
        // แปลง todayDate ('YYYY-MM-DD') เป็นช่วงเวลา LocalDateTime
        LocalDate date = LocalDate.parse(start);
        LocalDate endDate = LocalDate.parse(end);
        LocalDateTime startOfDay = date.atStartOfDay();                // 00:00:00
        LocalDateTime endOfDay = endDate.atTime(23, 59, 59);              // 23:59:59

        StringBuilder sql = new StringBuilder();
        Map<String, Object> params = new HashMap<>();

        sql.append("select site.id, site.code, site.name_th, site.name_en, site.ip_address, site.default_gateway, site.subnet_mask, ")
                .append("site.port, site.site_key, site.status, site.active, site.is_install, site.installed_date, site.lat, site.lon, ")
                .append("site.contact_point_name, site.contact_point_tel, site.address, site.key_generating, site.last_key_generate, ")
                .append("site.last_key_update, site.report_status_at, site.emerg_key_success, site.version, site.motion, ")
                .append("site.firmware_option, region.name as region, province.name_th as province, log.check_time, log.status as online, ")
                .append("log.is_ping_ip, log.is_ping_gateway, log.is_call_helpcheck, log.status_ping_ip, log.status_ping_gateway, log.status_call_helpcheck, log.json_result ")
                .append("from smartsite.site site ")
                .append("join smartsite.region region on site.region_id = region.id ")
                .append("join smartsite.province province on site.province_id = province.id ")
                .append("join smartsite.site_online_status_log log on site.code = log.site_code ")
                .append("where log.check_time between :start and :end ");

        params.put("start", startOfDay);
        params.put("end", endOfDay);

        if (siteCode != null && !siteCode.isEmpty()) {
            sql.append(" and site.code ilike :siteCode ");
            params.put("siteCode", "%" + siteCode + "%");
        }

        // ตรวจสอบ regionCode (ตอนนี้ควรเป็น String)
        if (regionCode != null && !regionCode.isEmpty()) {
            // เปลี่ยนจาก region.id = เป็น region.code ilike
            sql.append(" and region.code ilike :regionCode ");
            // เพิ่ม wildcard '%' สำหรับการค้นหาแบบ partial match
            params.put("regionCode", "%" + regionCode + "%");
        }

// ตรวจสอบ provinceCode (ตอนนี้ควรเป็น String)
        if (provinceCode != null && !provinceCode.isEmpty()) {
            // เปลี่ยนจาก province.id = เป็น province.code ilike
            sql.append(" and province.name_th ilike :provinceCode ");
            // เพิ่ม wildcard '%'
            params.put("provinceCode", "%" + provinceCode + "%");
        }

        if (onlineStatus != null) {
            sql.append(" and log.status = :onlineStatus ");
            params.put("onlineStatus", onlineStatus);
        }

        sql.append(" order by log.check_time desc");



        Query query = em.createNativeQuery(sql.toString());

        // set parameters
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            query.setParameter(entry.getKey(), entry.getValue());
        }

        List<Object[]> results = query.getResultList();
        List<OnlineStatusDetailResponse> todayDetail = new ArrayList<>();

        for (Object[] row : results) {
            OnlineStatusDetailResponse onlineOffline = new OnlineStatusDetailResponse();

            // การแปลงค่าจะขึ้นอยู่กับชนิดข้อมูลที่ได้จาก Database Driver
            // โดยทั่วไป ตัวเลขจะเป็น BigInteger/BigDecimal, วันที่จะเป็น Timestamp
            onlineOffline.setId(row[0] != null ? ((Number) row[0]).longValue() : null);
            onlineOffline.setCode((String) row[1]);
            onlineOffline.setNameTh((String) row[2]);
            onlineOffline.setNameEn((String) row[3]);
            onlineOffline.setIpAddress((String) row[4]);
            onlineOffline.setDefaultGateway((String) row[5]);
            onlineOffline.setSubnetMask((String) row[6]);
            onlineOffline.setPort(row[7] != null ? ((Number) row[7]).intValue() : null);
            onlineOffline.setSiteKey((String) row[8]);
            onlineOffline.setStatus(row[9] != null ? ((Number) row[9]).intValue() : null);
            onlineOffline.setActive((Boolean) row[10]);
            onlineOffline.setIsInstall((Boolean) row[11]);
            onlineOffline.setInstalledDate(row[12] != null ? ((java.sql.Timestamp) row[12]).toLocalDateTime() : null);
            onlineOffline.setLat((String) row[13]);
            onlineOffline.setLon((String) row[14]);
            onlineOffline.setContactPointName((String) row[15]);
            onlineOffline.setContactPointTel((String) row[16]);
            onlineOffline.setAddress((String) row[17]);
            onlineOffline.setKeyGenerating((Boolean) row[18]);
            onlineOffline.setLastKeyGenerate(row[19] != null ? ((java.sql.Timestamp) row[19]).toLocalDateTime() : null);
            onlineOffline.setLastKeyUpdate(row[20] != null ? ((java.sql.Timestamp) row[20]).toLocalDateTime() : null);
            onlineOffline.setReportStatusAt(row[21] != null ? ((java.sql.Timestamp) row[21]).toLocalDateTime() : null);
            onlineOffline.setEmergKeySuccess((Boolean) row[22]);
            onlineOffline.setVersion((String) row[23]);
            onlineOffline.setMotion(row[24] != null ? ((Number) row[24]).intValue() : null);
            onlineOffline.setFirmwareOption(row[25] != null ? ((Number) row[25]).intValue() : null);
            onlineOffline.setRegion((String) row[26]);
            onlineOffline.setProvince((String) row[27]);
            onlineOffline.setCheckTime(row[28] != null ? ((java.sql.Timestamp) row[28]).toLocalDateTime() : null);
            onlineOffline.setOnlineStatus((Boolean) row[29]);
            onlineOffline.setIsPingIp((Boolean) row[30]);
            onlineOffline.setIsPingGateway((Boolean) row[31]);
            onlineOffline.setIsCallHelpcheck((Boolean) row[32]);
            onlineOffline.setStatusPingIp((Boolean) row[33]);
            onlineOffline.setStatusPingGateway((Boolean) row[34]);
            onlineOffline.setStatusCallHelpcheck((Boolean) row[35]);
            onlineOffline.setJsonResult((String) row[36]);
            todayDetail.add(onlineOffline);
        }



        return todayDetail;
    }

    @Override
    public Page<OnlineStatusDetailResponse> findOnlineOfflineCheckDetailStatusesPaged(
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
        LocalDate date = LocalDate.parse(start);
        LocalDate endDateParsed = LocalDate.parse(end);
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = endDateParsed.atTime(23, 59, 59);

        StringBuilder sql = new StringBuilder();
        Map<String, Object> params = new HashMap<>();

        sql.append("select site.id, site.code, site.name_th, site.name_en, site.ip_address, site.default_gateway, site.subnet_mask, ")
                .append("site.port, site.site_key, site.status, site.active, site.is_install, site.installed_date, site.lat, site.lon, ")
                .append("site.contact_point_name, site.contact_point_tel, site.address, site.key_generating, site.last_key_generate, ")
                .append("site.last_key_update, site.report_status_at, site.emerg_key_success, site.version, site.motion, ")
                .append("site.firmware_option, region.name as region, province.name_th as province, log.check_time, log.status as online, ")
                .append("log.is_ping_ip, log.is_ping_gateway, log.is_call_helpcheck, log.status_ping_ip, log.status_ping_gateway, log.status_call_helpcheck, log.json_result ")
                .append("from smartsite.site site ")
                .append("join smartsite.region region on site.region_id = region.id ")
                .append("join smartsite.province province on site.province_id = province.id ")
                .append("join smartsite.site_online_status_log log on site.code = log.site_code ")
                .append("where log.check_time between :start and :end ");

        params.put("start", startOfDay);
        params.put("end", endOfDay);

        if (siteCode != null && !siteCode.isEmpty()) {
            sql.append(" and site.code ilike :siteCode ");
            params.put("siteCode", "%" + siteCode + "%");
        }
        if (regionCode != null && !regionCode.isEmpty()) {
            sql.append(" and region.code ilike :regionCode ");
            params.put("regionCode", "%" + regionCode + "%");
        }
        if (provinceCode != null && !provinceCode.isEmpty()) {
            sql.append(" and province.name_th ilike :provinceCode ");
            params.put("provinceCode", "%" + provinceCode + "%");
        }
        if (onlineStatus != null) {
            sql.append(" and log.status = :onlineStatus ");
            params.put("onlineStatus", onlineStatus);
        }

        // Ping IP filter: "success" = true, "fail" = false
        if (pingIpStatus != null && !pingIpStatus.isEmpty()) {
            if ("success".equalsIgnoreCase(pingIpStatus)) {
                sql.append(" and log.is_ping_ip = true ");
            } else if ("fail".equalsIgnoreCase(pingIpStatus)) {
                sql.append(" and log.is_ping_ip = false ");
            } else if ("wait".equalsIgnoreCase(pingIpStatus)) {
                sql.append(" and log.is_ping_ip is null ");
            }
        }

        // Ping GW filter: "success" = true, "fail" = false
        if (pingGwStatus != null && !pingGwStatus.isEmpty()) {
            if ("success".equalsIgnoreCase(pingGwStatus)) {
                sql.append(" and log.is_ping_gateway = true ");
            } else if ("fail".equalsIgnoreCase(pingGwStatus)) {
                sql.append(" and log.is_ping_gateway = false ");
            } else if ("wait".equalsIgnoreCase(pingGwStatus)) {
                sql.append(" and log.is_ping_gateway is null ");
            }
        }

        // Health check only: แสดงเฉพาะ site ที่ได้รับ JSON กลับมา
        if (Boolean.TRUE.equals(healthCheckOnly)) {
            sql.append(" and log.is_call_helpcheck = true ");
            sql.append(" and log.json_result is not null and log.json_result != '' ");
        }

        sql.append(" order by log.check_time desc");

        // Count query
        String countSql = "select count(*) from (" + sql.toString() + ") as total";
        Query countQuery = em.createNativeQuery(countSql);
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            countQuery.setParameter(entry.getKey(), entry.getValue());
        }
        long totalCount = ((Number) countQuery.getSingleResult()).longValue();

        // Data query with pagination
        Query query = em.createNativeQuery(sql.toString());
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            query.setParameter(entry.getKey(), entry.getValue());
        }
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());

        List<Object[]> results = query.getResultList();
        List<OnlineStatusDetailResponse> todayDetail = new ArrayList<>();

        for (Object[] row : results) {
            OnlineStatusDetailResponse onlineOffline = new OnlineStatusDetailResponse();
            onlineOffline.setId(row[0] != null ? ((Number) row[0]).longValue() : null);
            onlineOffline.setCode((String) row[1]);
            onlineOffline.setNameTh((String) row[2]);
            onlineOffline.setNameEn((String) row[3]);
            onlineOffline.setIpAddress((String) row[4]);
            onlineOffline.setDefaultGateway((String) row[5]);
            onlineOffline.setSubnetMask((String) row[6]);
            onlineOffline.setPort(row[7] != null ? ((Number) row[7]).intValue() : null);
            onlineOffline.setSiteKey((String) row[8]);
            onlineOffline.setStatus(row[9] != null ? ((Number) row[9]).intValue() : null);
            onlineOffline.setActive((Boolean) row[10]);
            onlineOffline.setIsInstall((Boolean) row[11]);
            onlineOffline.setInstalledDate(row[12] != null ? ((java.sql.Timestamp) row[12]).toLocalDateTime() : null);
            onlineOffline.setLat((String) row[13]);
            onlineOffline.setLon((String) row[14]);
            onlineOffline.setContactPointName((String) row[15]);
            onlineOffline.setContactPointTel((String) row[16]);
            onlineOffline.setAddress((String) row[17]);
            onlineOffline.setKeyGenerating((Boolean) row[18]);
            onlineOffline.setLastKeyGenerate(row[19] != null ? ((java.sql.Timestamp) row[19]).toLocalDateTime() : null);
            onlineOffline.setLastKeyUpdate(row[20] != null ? ((java.sql.Timestamp) row[20]).toLocalDateTime() : null);
            onlineOffline.setReportStatusAt(row[21] != null ? ((java.sql.Timestamp) row[21]).toLocalDateTime() : null);
            onlineOffline.setEmergKeySuccess((Boolean) row[22]);
            onlineOffline.setVersion((String) row[23]);
            onlineOffline.setMotion(row[24] != null ? ((Number) row[24]).intValue() : null);
            onlineOffline.setFirmwareOption(row[25] != null ? ((Number) row[25]).intValue() : null);
            onlineOffline.setRegion((String) row[26]);
            onlineOffline.setProvince((String) row[27]);
            onlineOffline.setCheckTime(row[28] != null ? ((java.sql.Timestamp) row[28]).toLocalDateTime() : null);
            onlineOffline.setOnlineStatus((Boolean) row[29]);
            onlineOffline.setIsPingIp((Boolean) row[30]);
            onlineOffline.setIsPingGateway((Boolean) row[31]);
            onlineOffline.setIsCallHelpcheck((Boolean) row[32]);
            onlineOffline.setStatusPingIp((Boolean) row[33]);
            onlineOffline.setStatusPingGateway((Boolean) row[34]);
            onlineOffline.setStatusCallHelpcheck((Boolean) row[35]);
            onlineOffline.setJsonResult((String) row[36]);
            todayDetail.add(onlineOffline);
        }

        return new PageImpl<>(todayDetail, pageable, totalCount);
    }
}
