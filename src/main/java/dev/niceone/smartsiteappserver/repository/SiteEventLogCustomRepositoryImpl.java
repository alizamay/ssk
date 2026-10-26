package dev.niceone.smartsiteappserver.repository;

import dev.niceone.smartsiteappserver.model.SiteEventLog;
import dev.niceone.smartsiteappserver.model.SiteEventLogWithCount;
import dev.niceone.smartsiteappserver.model.SiteEventLogWithResolve;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class SiteEventLogCustomRepositoryImpl implements SiteEventLogCustomRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<SiteEventLogWithCount> getAllUnresolvedLogsWithCounts(String keyword, LocalDateTime start, LocalDateTime end) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT l.*, s.count_create_event, s.count_resolve_event ")
                .append("FROM smartsite.site_event_log l ")
                .append("INNER JOIN smartsite.site_event_stat_summary s ON l.id = s.log_id ")
                .append("WHERE l.is_resolve = false");

        Map<String, Object> params = new HashMap<>();

        if (start != null) {
            sql.append(" AND l.create_date >= :start");
            params.put("start", start);
        }

        if (end != null) {
            sql.append(" AND l.create_date <= :end");
            params.put("end", end);
        }

        if (keyword != null && !keyword.trim().isEmpty()) {
            sql.append(" AND (");
            sql.append(" LOWER(l.alarm_type_name) LIKE LOWER(:kw) OR");
            sql.append(" LOWER(l.site_code) LIKE LOWER(:kw) OR");
            sql.append(" LOWER(l.region_code) LIKE LOWER(:kw) OR");
            sql.append(" LOWER(l.cabinet_code) LIKE LOWER(:kw)");
            sql.append(")");
            params.put("kw", "%" + keyword.trim() + "%");
        }

        sql.append(" ORDER BY l.create_date DESC");

        NamedParameterJdbcTemplate namedJdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
        return namedJdbc.query(sql.toString(), params, (rs, rowNum) -> {
            SiteEventLogWithCount log = new SiteEventLogWithCount();
            log.setId(UUID.fromString(rs.getString("id")));
            Timestamp createTimestamp = rs.getTimestamp("create_date");
            if (createTimestamp != null) log.setCreateDate(createTimestamp.toLocalDateTime());
            log.setActive(rs.getBoolean("active"));
            log.setAlarmType(rs.getInt("alarm_type"));
            log.setCabinetId(rs.getString("cabinet_id"));
            log.setDeviceId(rs.getString("device_id"));
            Timestamp resolveTimestamp = rs.getTimestamp("resolve_date");
            if (resolveTimestamp != null) log.setResolveDate(resolveTimestamp.toLocalDateTime());
            log.setSiteId(rs.getString("site_id"));
            log.setSiteCode(rs.getString("site_code"));
            log.setSiteNameTh(rs.getString("site_name_th"));
            log.setSiteNameEn(rs.getString("site_name_en"));
            Timestamp updateTimestamp = rs.getTimestamp("update_date");
            if (updateTimestamp != null) log.setUpdateDate(updateTimestamp.toLocalDateTime());
            log.setWriteToFile(rs.getBoolean("write_to_file"));
            log.setAlarmTypeCode(rs.getString("alarm_type_code"));
            log.setAlarmTypeName(rs.getString("alarm_type_name"));
            log.setCabinetNo(rs.getString("cabinet_no"));
            log.setCabinetCode(rs.getString("cabinet_code"));
            log.setLockEventId(rs.getInt("lock_event_id"));
            log.setLockEventName(rs.getString("lock_event_name"));
            log.setDoorEventId(rs.getInt("door_event_id"));
            log.setDoorEventName(rs.getString("door_event_name"));
            log.setStatusCode(rs.getString("status_code"));
            log.setRegionCode(rs.getString("region_code"));
            log.setRegionName(rs.getString("region_name"));
            log.setIsResolve(rs.getBoolean("is_resolve"));
            log.setCountCreateEvent(rs.getInt("count_create_event"));
            log.setCountResolveEvent(rs.getInt("count_resolve_event"));

            return log;
        });
    }

    public Page<SiteEventLog> findResolvedLogsWithFilters(
            String keyword,
            List<Integer> alarmTypes,
            LocalDateTime start,
            LocalDateTime end,
            int page,
            int size
    ) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT log.* FROM smartsite.site_event_log log ")
                .append("INNER JOIN smartsite.site_event_stat_summary summary ON log.id = summary.log_id ")
                .append("WHERE summary.count_resolve_event > 0 ");

        // Optional: query count for total elements
        StringBuilder countSql = new StringBuilder();
        countSql.append("SELECT COUNT(*) FROM smartsite.site_event_log log ")
                .append("INNER JOIN smartsite.site_event_stat_summary summary ON log.id = summary.log_id ")
                .append("WHERE summary.count_resolve_event > 0 ");

        List<Object> params = new ArrayList<>();
        List<Object> countParams = new ArrayList<>();

        if (keyword != null && !keyword.trim().isEmpty()) {
            sql.append("AND (LOWER(log.alarm_type_name) LIKE ? ")
                    .append("OR LOWER(log.site_code) LIKE ? ")
                    .append("OR LOWER(log.region_code) LIKE ? ")
                    .append("OR LOWER(log.cabinet_code) LIKE ?) ");
            countSql.append("AND (LOWER(log.alarm_type_name) LIKE ? ")
                    .append("OR LOWER(log.site_code) LIKE ? ")
                    .append("OR LOWER(log.region_code) LIKE ? ")
                    .append("OR LOWER(log.cabinet_code) LIKE ?) ");
            String kw = "%" + keyword.toLowerCase().trim() + "%";
            for (int i = 0; i < 4; i++) {
                params.add(kw);
                countParams.add(kw);
            }
        }

        if (alarmTypes != null && !alarmTypes.isEmpty()) {
            String inClause = alarmTypes.stream()
                    .map(type -> "?")
                    .collect(Collectors.joining(", "));
            sql.append("AND log.alarm_type IN (").append(inClause).append(") ");
            countSql.append("AND log.alarm_type IN (").append(inClause).append(") ");
            params.addAll(alarmTypes);
            countParams.addAll(alarmTypes);
        }

        if (start != null) {
            sql.append("AND log.create_date >= ? ");
            countSql.append("AND log.create_date >= ? ");
            params.add(Timestamp.valueOf(start));
            countParams.add(Timestamp.valueOf(start));
        }
        if (end != null) {
            sql.append("AND log.create_date <= ? ");
            countSql.append("AND log.create_date <= ? ");
            params.add(Timestamp.valueOf(end));
            countParams.add(Timestamp.valueOf(end));
        }

        // Order & Pagination
        sql.append("ORDER BY log.create_date DESC ");
        sql.append("LIMIT ? OFFSET ? ");
        params.add(size);
        params.add(page * size);

        // Query
        List<SiteEventLog> results = jdbcTemplate.query(
                sql.toString(),
                params.toArray(),
                new BeanPropertyRowMapper<>(SiteEventLog.class)
        );

        int total = jdbcTemplate.queryForObject(countSql.toString(), countParams.toArray(), Integer.class);

        return new PageImpl<>(results, PageRequest.of(page, size), total);
    }


    @Override
    public List<SiteEventLogWithResolve> getAllResolvedLogsByAlarmTypes(String keyword, List<Integer> alarmTypes, LocalDateTime start, LocalDateTime end) {
        StringBuilder sql = new StringBuilder();
        sql.append("select l.*,h.create_date as event_create_date,h.resolve_date as event_clear_date ")
                .append("FROM smartsite.site_event_log l ")
                .append("INNER JOIN smartsite.site_event_resolve_history h ON l.id = h.log_id ");

        MapSqlParameterSource params = new MapSqlParameterSource();
        List<String> conditions = new ArrayList<>();

        if (start != null) {
            conditions.add("l.create_date >= :start");
            params.addValue("start", start);
        }

        if (end != null) {
            conditions.add("l.create_date <= :end");
            params.addValue("end", end);
        }

        if (alarmTypes != null && !alarmTypes.isEmpty()) {
            conditions.add("l.alarm_type IN (:alarmTypes)");
            params.addValue("alarmTypes", alarmTypes);
        }

        if (keyword != null && !keyword.trim().isEmpty()) {
            conditions.add("(" +
                    "LOWER(l.alarm_type_name) LIKE LOWER(:kw) OR " +
                    "LOWER(l.site_code) LIKE LOWER(:kw) OR " +
                    "LOWER(l.region_code) LIKE LOWER(:kw) OR " +
                    "LOWER(l.cabinet_code) LIKE LOWER(:kw))");
            params.addValue("kw", "%" + keyword.trim() + "%");
        }

        if (!conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }

        sql.append(" ORDER BY l.create_date DESC");

        NamedParameterJdbcTemplate namedJdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
        return namedJdbc.query(sql.toString(), params, (rs, rowNum) -> {
            SiteEventLogWithResolve log = new SiteEventLogWithResolve();
            log.setId(UUID.fromString(rs.getString("id")));
            Timestamp createTimestamp = rs.getTimestamp("create_date");
            if (createTimestamp != null) log.setCreateDate(createTimestamp.toLocalDateTime());
            log.setActive(rs.getBoolean("active"));
            log.setAlarmType(rs.getInt("alarm_type"));
            log.setCabinetId(rs.getString("cabinet_id"));
            log.setDeviceId(rs.getString("device_id"));
            Timestamp resolveTimestamp = rs.getTimestamp("resolve_date");
            if (resolveTimestamp != null) log.setResolveDate(resolveTimestamp.toLocalDateTime());
            log.setSiteId(rs.getString("site_id"));
            log.setSiteCode(rs.getString("site_code"));
            log.setSiteNameTh(rs.getString("site_name_th"));
            log.setSiteNameEn(rs.getString("site_name_en"));
            Timestamp updateTimestamp = rs.getTimestamp("update_date");
            if (updateTimestamp != null) log.setUpdateDate(updateTimestamp.toLocalDateTime());
            log.setWriteToFile(rs.getBoolean("write_to_file"));
            log.setAlarmTypeCode(rs.getString("alarm_type_code"));
            log.setAlarmTypeName(rs.getString("alarm_type_name"));
            log.setCabinetNo(rs.getString("cabinet_no"));
            log.setCabinetCode(rs.getString("cabinet_code"));
            log.setLockEventId(rs.getInt("lock_event_id"));
            log.setLockEventName(rs.getString("lock_event_name"));
            log.setDoorEventId(rs.getInt("door_event_id"));
            log.setDoorEventName(rs.getString("door_event_name"));
            log.setStatusCode(rs.getString("status_code"));
            log.setRegionCode(rs.getString("region_code"));
            log.setRegionName(rs.getString("region_name"));
            log.setIsResolve(rs.getBoolean("is_resolve"));
            Timestamp eventCreateTimestamp = rs.getTimestamp("event_create_date");
            if (eventCreateTimestamp != null) log.setEventCreateDate(eventCreateTimestamp.toLocalDateTime());
            Timestamp eventClearTimestamp = rs.getTimestamp("event_clear_date");
            if (eventClearTimestamp != null) log.setEventClearDate(eventClearTimestamp.toLocalDateTime());

            return log;
        });
    }

}
