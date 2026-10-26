package dev.niceone.smartsiteappserver.repository;

import dev.niceone.smartsiteappserver.model.OnsiteCheckLog;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class OnsiteCheckLogRepositoryImpl implements OnsiteCheckLogRepositoryCustom {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<OnsiteCheckLog> ROW_MAPPER = (rs, rowNum) -> {
        OnsiteCheckLog log = new OnsiteCheckLog();
        log.setId(rs.getLong("id"));
        log.setSiteCode(rs.getString("site_code"));
        log.setClientIp(rs.getString("client_ip"));
        Timestamp ts = rs.getTimestamp("request_time");
        if (ts != null) log.setRequestTime(ts.toLocalDateTime());
        log.setResponseStatus(rs.getObject("response_status", Integer.class));
        log.setResponseRaw(rs.getString("response_raw"));
        return log;
    };

    @Override
    public Page<OnsiteCheckLog> findByFilters(String siteCode, LocalDateTime startTime,
                                               LocalDateTime endTime, Integer responseStatus,
                                               Pageable pageable) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (siteCode != null && !siteCode.isEmpty()) {
            where.append(" AND site_code = ?");
            params.add(siteCode);
        }
        if (startTime != null) {
            where.append(" AND request_time >= ?");
            params.add(Timestamp.valueOf(startTime));
        }
        if (endTime != null) {
            where.append(" AND request_time <= ?");
            params.add(Timestamp.valueOf(endTime));
        }
        if (responseStatus != null) {
            where.append(" AND response_status = ?");
            params.add(responseStatus);
        }

        String baseFrom = "FROM smartsite.onsite_check_log" + where;

        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) " + baseFrom,
                params.toArray(), Long.class);
        if (total == null) total = 0L;

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageable.getPageSize());
        pageParams.add(pageable.getOffset());

        List<OnsiteCheckLog> rows = jdbcTemplate.query(
                "SELECT * " + baseFrom + " ORDER BY request_time DESC LIMIT ? OFFSET ?",
                pageParams.toArray(), ROW_MAPPER);

        return new PageImpl<>(rows, pageable, total);
    }

    @Override
    public List<OnsiteCheckLog> findAllByFilters(String siteCode, LocalDateTime startTime,
                                                  LocalDateTime endTime, Integer responseStatus) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (siteCode != null && !siteCode.isEmpty()) {
            where.append(" AND site_code = ?");
            params.add(siteCode);
        }
        if (startTime != null) {
            where.append(" AND request_time >= ?");
            params.add(Timestamp.valueOf(startTime));
        }
        if (endTime != null) {
            where.append(" AND request_time <= ?");
            params.add(Timestamp.valueOf(endTime));
        }
        if (responseStatus != null) {
            where.append(" AND response_status = ?");
            params.add(responseStatus);
        }

        return jdbcTemplate.query(
                "SELECT * FROM smartsite.onsite_check_log" + where + " ORDER BY request_time DESC",
                params.toArray(), ROW_MAPPER);
    }
}
