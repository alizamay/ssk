package dev.niceone.smartsiteappserver.repository;

import dev.niceone.smartsiteappserver.model.SiteOnlineStatusLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.List;

@Repository
public class SiteOnlineStatusLogRepositoryCustomImpl {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String UPSERT_SQL =
            "INSERT INTO smartsite.site_online_status_log " +
                    "(site_code, status, period, check_time, ip_address, default_gateway, status_ping_ip, status_ping_gateway, status_call_helpcheck, is_ping_ip, is_ping_gateway, is_call_helpcheck, json_result, port) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON CONFLICT (site_code, period, (check_time::date)) DO UPDATE SET " +
                    "status = EXCLUDED.status, " +
                    "check_time = EXCLUDED.check_time, " +
                    "ip_address = EXCLUDED.ip_address, " +
                    "default_gateway = EXCLUDED.default_gateway, " +
                    "status_ping_ip = EXCLUDED.status_ping_ip, " +
                    "status_ping_gateway = EXCLUDED.status_ping_gateway, " +
                    "status_call_helpcheck = EXCLUDED.status_call_helpcheck, " +
                    "is_ping_ip = EXCLUDED.is_ping_ip, " +
                    "is_ping_gateway = EXCLUDED.is_ping_gateway, " +
                    "is_call_helpcheck = EXCLUDED.is_call_helpcheck, " +
                    "json_result = EXCLUDED.json_result, " +
                    "port = EXCLUDED.port";

    /**
     * INSERT...SELECT โดยตรงใน DB — ไม่โหลด Site objects ขึ้น Java เลย
     * เร็วกว่า batchInsert(findAll()) มากสำหรับ 30,000 sites
     * status ordinal: STATUS_DOWN=0, STATUS_UP=1, STATUS_UNKNOWN=2
     */
    public int insertStatusFromSites(java.time.LocalTime period) {
        // Idempotent + works on partitioned tables (ON CONFLICT cannot be used on a
        // partitioned table whose unique index does not include the partition key):
        // clear today's rows for this period first, then plain INSERT...SELECT.
        jdbcTemplate.update(
            "DELETE FROM smartsite.site_online_status_log " +
            "WHERE period = ?::time AND check_time::date = CURRENT_DATE",
            period.toString());

        String sql =
            "INSERT INTO smartsite.site_online_status_log " +
            "  (site_code, status, period, check_time, ip_address, default_gateway, " +
            "   status_ping_ip, status_ping_gateway, status_call_helpcheck, " +
            "   is_ping_ip, is_ping_gateway, is_call_helpcheck, json_result, port) " +
            "SELECT " +
            "  code, " +
            "  (status = 1), " +          // 1 = STATUS_UP ordinal
            "  ?::time, " +
            "  NOW(), " +
            "  ip_address, " +
            "  default_gateway, " +
            "  false, false, false, false, false, false, '', port " +
            "FROM smartsite.site " +
            "WHERE active = true AND is_install = true";

        return jdbcTemplate.update(sql, period.toString());
    }

    public void batchInsert(List<SiteOnlineStatusLog> logs) {
        jdbcTemplate.batchUpdate(UPSERT_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                SiteOnlineStatusLog log = logs.get(i);
                ps.setString(1, log.getSiteCode());
                ps.setBoolean(2, log.getStatus());
                ps.setTime(3, Time.valueOf(log.getPeriod()));
                ps.setTimestamp(4, Timestamp.valueOf(log.getCheckTime()));
                ps.setString(5, log.getIpAddress());
                ps.setString(6, log.getDefaultGateway());
                ps.setBoolean(7, log.getStatusPingIp());
                ps.setBoolean(8, log.getStatusPingGateway());
                ps.setBoolean(9, log.getStatusCallHelpcheck());
                ps.setBoolean(10, log.getIsPingIp());
                ps.setBoolean(11, log.getIsPingGateway());
                ps.setBoolean(12, log.getIsCallHelpcheck());
                ps.setString(13, log.getJsonResult());
                if (log.getPort() != null) {
                    ps.setInt(14, log.getPort());
                } else {
                    ps.setNull(14, java.sql.Types.INTEGER);
                }
            }

            @Override
            public int getBatchSize() {
                return logs.size();
            }
        });
    }

    public int updateStatusLog(Long id, Boolean pingIp, Boolean pingGateway, Boolean callHelpcheck, Boolean isPingIp, Boolean isPingGateway, Boolean isCallHelpcheck, String jsonResult) {
        String sql = "UPDATE smartsite.site_online_status_log SET " +
                "status_ping_ip = ?, " +
                "status_ping_gateway = ?, " +
                "status_call_helpcheck = ?, " +
                "is_ping_ip = ?, " +
                "is_ping_gateway = ?, " +
                "is_call_helpcheck = ?, " +
                "json_result = ? " +
                "WHERE id = ?";

        return jdbcTemplate.update(sql, pingIp, pingGateway, callHelpcheck, isPingIp, isPingGateway, isCallHelpcheck, jsonResult, id);
    }

    public int updatePingIP(Long id, Boolean pingIp, Boolean isPingIp) {
        String sql = "UPDATE smartsite.site_online_status_log SET " +
                "status_ping_ip = ?, " +
                "is_ping_ip = ? " +
                "WHERE id = ?";

        return jdbcTemplate.update(sql, pingIp, isPingIp, id);
    }

    public int updatePingGateway(Long id, Boolean pingGateway, Boolean isPingGateway) {
        String sql = "UPDATE smartsite.site_online_status_log SET " +
                "status_ping_gateway = ?, " +
                "is_ping_gateway = ? " +
                "WHERE id = ?";

        return jdbcTemplate.update(sql, pingGateway, isPingGateway, id);
    }

    public int updateCallHelpcheck(Long id, Boolean callHelpcheck, Boolean isCallHelpcheck,String jsonResult) {
        String sql = "UPDATE smartsite.site_online_status_log SET " +
                "status_call_helpcheck = ?, " +
                "is_call_helpcheck = ?, " +
                "json_result = ? " +
                "WHERE id = ?";

        return jdbcTemplate.update(sql, callHelpcheck, isCallHelpcheck, jsonResult, id);
    }
}
