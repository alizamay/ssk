package dev.niceone.smartsiteappserver.service;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

/**
 * Connection Pool Monitoring Service
 *
 * แก้ปัญหา: HikariPool Timeout (8,717 errors จาก log analysis)
 * - Monitor pool usage ทุก 1 นาที
 * - Alert เมื่อ utilization > 80%
 * - ไม่ต้องใช้ library เพิ่มเติม ใช้ HikariCP MBean ที่มีอยู่แล้ว
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class ConnectionPoolMonitorService {

    private final DataSource dataSource;

    private static final int ALERT_THRESHOLD = 80; // Alert when > 80% used
    private int consecutiveHighUsage = 0;

    @Scheduled(fixedRate = 60000) // ทุก 1 นาที
    public void monitorConnectionPool() {
        if (!(dataSource instanceof HikariDataSource)) {
            return;
        }

        try {
            HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
            HikariPoolMXBean poolBean = hikariDataSource.getHikariPoolMXBean();

            if (poolBean == null) {
                log.warn("HikariPoolMXBean is not available. Set spring.datasource.hikari.registerMbeans=true");
                return;
            }

            int active = poolBean.getActiveConnections();
            int idle = poolBean.getIdleConnections();
            int total = poolBean.getTotalConnections();
            int waiting = poolBean.getThreadsAwaitingConnection();
            int maxPoolSize = hikariDataSource.getMaximumPoolSize();

            float utilizationPct = total > 0 ? ((float) active / total * 100) : 0;
            float capacityPct = maxPoolSize > 0 ? ((float) total / maxPoolSize * 100) : 0;

            // Alert ถ้า utilization สูง
            if (utilizationPct > ALERT_THRESHOLD) {
                consecutiveHighUsage++;

                if (consecutiveHighUsage >= 3) {
                    log.error("⚠️ CRITICAL: Connection Pool High Utilization for {} minutes! " +
                            "Utilization={:.1f}% Active={} Idle={} Total={}/{} Waiting={}",
                            consecutiveHighUsage, utilizationPct, active, idle, total, maxPoolSize, waiting);
                } else {
                    log.warn("⚠️ WARNING: Connection Pool Utilization={:.1f}% Active={} Idle={} Total={}/{} Waiting={}",
                            utilizationPct, active, idle, total, maxPoolSize, waiting);
                }
            } else {
                consecutiveHighUsage = 0;
                log.info("✓ Pool Health: Utilization={:.1f}% Capacity={:.1f}% Active={} Idle={} Total={}/{} Waiting={}",
                        utilizationPct, capacityPct, active, idle, total, maxPoolSize, waiting);
            }

            // Alert ถ้ามี threads กำลังรอ connection
            if (waiting > 0) {
                log.error("⚠️ BOTTLENECK DETECTED: {} threads waiting for DB connection! " +
                        "Active={} Total={} MaxPool={}",
                        waiting, active, total, maxPoolSize);
            }

        } catch (Exception e) {
            log.error("Error monitoring connection pool", e);
        }
    }

    /**
     * Log pool statistics to file ทุก 5 นาที สำหรับ analysis ภายหลัง
     */
    @Scheduled(fixedRate = 300000) // ทุก 5 นาที
    public void logPoolStatistics() {
        if (!(dataSource instanceof HikariDataSource)) {
            return;
        }

        try {
            HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
            HikariPoolMXBean poolBean = hikariDataSource.getHikariPoolMXBean();

            if (poolBean == null) {
                return;
            }

            int active = poolBean.getActiveConnections();
            int idle = poolBean.getIdleConnections();
            int total = poolBean.getTotalConnections();
            int waiting = poolBean.getThreadsAwaitingConnection();

            // Log เป็น CSV format เพื่อง่ายต่อการวิเคราะห์
            log.info("POOL_STATS,{},{},{},{},{}",
                    System.currentTimeMillis(), active, idle, total, waiting);

        } catch (Exception e) {
            log.error("Error logging pool statistics", e);
        }
    }
}
