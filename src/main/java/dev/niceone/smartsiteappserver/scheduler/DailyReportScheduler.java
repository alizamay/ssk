package dev.niceone.smartsiteappserver.scheduler;

import dev.niceone.smartsiteappserver.model.SiteOnlineStatusLog;
import dev.niceone.smartsiteappserver.repository.SiteOnlineStatusLogRepository;
import dev.niceone.smartsiteappserver.repository.SiteOnlineStatusLogRepositoryCustomImpl;
import dev.niceone.smartsiteappserver.service.SchedulerConfigService;
import dev.niceone.smartsiteappserver.service.SchedulerLockService;
import dev.niceone.smartsiteappserver.utils.AppConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Scheduler แยกสำหรับ Daily Report (สถานะรายวัน + ping verify)
 * แยกออกจาก SiteScheduler เพื่อลด resource contention
 */
@Slf4j
@Component
public class DailyReportScheduler {

    @Autowired
    private Environment env;

    @Autowired
    private SiteOnlineStatusLogRepository siteOnlineStatusLogRepository;

    @Autowired
    private SiteOnlineStatusLogRepositoryCustomImpl siteOnlineStatusLogRepositoryCustomImpl;

    @Autowired
    private dev.niceone.smartsiteappserver.service.SiteDistributionService siteDistributionService;

    @Autowired
    private SchedulerConfigService schedulerConfigService;

    @Autowired
    private SchedulerLockService schedulerLockService;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("pingExecutor")
    private java.util.concurrent.Executor pingExecutor;

    @Value("${app.ping.timeout-ms:500}")
    private int pingTimeoutMs;

    // ─── Scheduled entry points ────────────────────────────────────────────────

    @Async
    @Scheduled(cron = "${app.site_status_corn:0 0 4 * * *}")
    public void insertMorningStatus() {
        if (!isEligible()) return;
        insertStatusForPeriod(parsePeriodFromCron(env.getProperty("app.site_status_corn", "0 0 4 * * *")));
    }

    /**
     * เรียกจากหน้ารายงาน — เคลียร์ข้อมูลวันนี้แล้วเก็บใหม่
     * ใช้ period จาก config ที่ตั้งไว้ใน scheduler
     */
    public void recollectToday() {
        String cronExpr = env.getProperty("app.site_status_corn", "0 0 4 * * *");
        LocalTime period = parsePeriodFromCron(cronExpr);
        log.info("Recollect today: clearing existing records for period={}", period);

        int deleted = siteOnlineStatusLogRepository.deleteByTodayAndPeriod(period);
        log.info("Deleted {} records for today period={}", deleted, period);

        insertStatusForPeriod(period);
    }

    private LocalTime parsePeriodFromCron(String cron) {
        try {
            // cron format: second minute hour ...
            String[] parts = cron.trim().split("\\s+");
            int hour = Integer.parseInt(parts[2]);
            int minute = Integer.parseInt(parts[1]);
            return LocalTime.of(hour, minute);
        } catch (Exception e) {
            log.warn("Could not parse period from cron '{}', defaulting to 04:00", cron);
            return LocalTime.of(4, 0);
        }
    }

    @Async
    @Scheduled(cron = "${app.health_check_corn:0 15 4 * * *}")
    public void checkHealthCheckStatus() {
        if (!isHealthCheckEligible()) return;
        LocalTime period = parsePeriodFromCron(env.getProperty("app.site_status_corn", "0 0 4 * * *"));
        log.info("Executing health check (node={})", siteDistributionService.getNodeId());
        checkHealthCheckInternal(period);
    }

    @Async
    @Scheduled(cron = "${app.offline_check_corn:0 15 6 * * *}")
    public void checkProveIsOffline() {
        if (!isPingEligible()) return;
        boolean isEnabled = schedulerConfigService.getBooleanConfig("app.offline_check",
                !"no".equals(env.getProperty("app.offline_check", "").trim()));
        if (!isEnabled) {
            log.info("offline_check disabled — skipping");
            return;
        }
        log.info("Executing offline check (node={})", siteDistributionService.getNodeId());
        checkProveIsOfflineInternal(parsePeriodFromCron(env.getProperty("app.site_status_corn", "0 0 4 * * *")));
    }

    @Async
    @Scheduled(cron = "${app.online_check_corn:0 20 6 * * *}")
    public void checkProveIsOnline() {
        if (!isPingEligible()) return;
        boolean isEnabled = schedulerConfigService.getBooleanConfig("app.online_check",
                !"no".equals(env.getProperty("app.online_check", "").trim()));
        if (!isEnabled) {
            log.info("online_check disabled — skipping");
            return;
        }
        log.info("Executing online check (node={})", siteDistributionService.getNodeId());
        checkProveIsOnlineInternal(parsePeriodFromCron(env.getProperty("app.site_status_corn", "0 0 4 * * *")));
    }

    // ─── Implementation ────────────────────────────────────────────────────────

    private void insertStatusForPeriod(LocalTime period) {
        try {
            log.info("Starting insertStatusForPeriod for period: {}", period);
            Instant start = Instant.now();

            int rows = siteOnlineStatusLogRepositoryCustomImpl.insertStatusFromSites(period);

            log.info("Completed insertStatusForPeriod | period: {} | rows: {} | elapsed: {}ms",
                    period, rows, Duration.between(start, Instant.now()).toMillis());

        } catch (Exception e) {
            log.error("Error in insertStatusForPeriod for period: {} | Error: {}", period, e.getMessage(), e);
        }
    }

    private void checkProveIsOfflineInternal(LocalTime period) {
        try {
            List<SiteOnlineStatusLog> offlineLogs;
            int batchNumber = 1;
            Instant totalStart = Instant.now();
            log.info("Starting offline check processing (timeout={}ms)", pingTimeoutMs);

            do {
                Pageable limit = PageRequest.of(0, 500);
                List<SiteOnlineStatusLog> allOfflineLogs = siteOnlineStatusLogRepository
                        .findByStatusFalseAndTodayPeriod(period, limit);
                offlineLogs = siteDistributionService.filterLogsForNode(allOfflineLogs);

                if (!offlineLogs.isEmpty()) {
                    log.info("Offline batch #{}: {} logs", batchNumber, offlineLogs.size());
                    Instant batchStart = Instant.now();

                    List<CompletableFuture<Void>> futures = offlineLogs.stream()
                            .map(siteLog -> CompletableFuture.runAsync(() -> {
                                try {
                                    processOfflineLogParallel(siteLog);
                                } catch (Exception e) {
                                    log.error("Error processing offline log: {} | {}", siteLog.getId(), e.getMessage());
                                }
                            }, pingExecutor))
                            .collect(Collectors.toList());

                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                    log.info("Offline batch #{} done in {}ms", batchNumber,
                            Duration.between(batchStart, Instant.now()).toMillis());
                    batchNumber++;
                }
            } while (!offlineLogs.isEmpty());

            log.info("Offline check completed in {} seconds", Duration.between(totalStart, Instant.now()).getSeconds());

        } catch (Exception e) {
            log.error("Error during offline check processing", e);
        }
    }

    private void checkProveIsOnlineInternal(LocalTime period) {
        try {
            List<SiteOnlineStatusLog> onlineLogs;
            int batchNumber = 1;
            Instant totalStart = Instant.now();
            log.info("Starting online check processing (timeout={}ms)", pingTimeoutMs);

            do {
                Pageable limit = PageRequest.of(0, 500);
                List<SiteOnlineStatusLog> allOnlineLogs = siteOnlineStatusLogRepository
                        .findByStatusTrueAndTodayPeriod(period, limit);
                onlineLogs = siteDistributionService.filterLogsForNode(allOnlineLogs);

                if (!onlineLogs.isEmpty()) {
                    log.info("Online batch #{}: {} logs", batchNumber, onlineLogs.size());
                    Instant batchStart = Instant.now();

                    List<CompletableFuture<Void>> futures = onlineLogs.stream()
                            .map(siteLog -> CompletableFuture.runAsync(() -> {
                                try {
                                    processOnlineLogParallel(siteLog);
                                } catch (Exception e) {
                                    log.error("Error processing online log: {} | {}", siteLog.getId(), e.getMessage());
                                }
                            }, pingExecutor))
                            .collect(Collectors.toList());

                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                    log.info("Online batch #{} done in {}ms", batchNumber,
                            Duration.between(batchStart, Instant.now()).toMillis());
                    batchNumber++;
                }
            } while (!onlineLogs.isEmpty());

            log.info("Online check completed in {} seconds", Duration.between(totalStart, Instant.now()).getSeconds());

        } catch (Exception e) {
            log.error("Error during online check processing", e);
        }
    }

    private void checkHealthCheckInternal(LocalTime period) {
        try {
            List<SiteOnlineStatusLog> logs;
            int batchNumber = 1;
            Instant totalStart = Instant.now();
            log.info("Starting health check (timeout={}ms)", pingTimeoutMs);

            do {
                Pageable limit = PageRequest.of(0, 500);
                List<SiteOnlineStatusLog> allOnlineLogs = siteOnlineStatusLogRepository
                        .findByStatusTrueAndTodayPeriod(period, limit);
                logs = siteDistributionService.filterLogsForNode(allOnlineLogs);

                if (!logs.isEmpty()) {
                    log.info("Health check batch #{}: {} sites", batchNumber, logs.size());
                    Instant batchStart = Instant.now();

                    List<CompletableFuture<Void>> futures = logs.stream()
                            .map(siteLog -> CompletableFuture.runAsync(() -> {
                                try {
                                    Pair<Boolean, String> result = callHealthCheck(siteLog);
                                    siteOnlineStatusLogRepositoryCustomImpl.updateCallHelpcheck(
                                            siteLog.getId(), result.getLeft(), true, result.getRight());
                                } catch (Exception e) {
                                    log.error("Health check error site: {} | {}", siteLog.getSiteCode(), e.getMessage());
                                }
                            }, pingExecutor))
                            .collect(Collectors.toList());

                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                    log.info("Health check batch #{} done in {}ms", batchNumber,
                            Duration.between(batchStart, Instant.now()).toMillis());
                    batchNumber++;
                }
            } while (!logs.isEmpty());

            log.info("Health check completed in {} seconds",
                    Duration.between(totalStart, Instant.now()).getSeconds());
        } catch (Exception e) {
            log.error("Error during health check", e);
        }
    }

    // ─── Ping / Healthcheck helpers ────────────────────────────────────────────

    private void processOfflineLogParallel(SiteOnlineStatusLog statusLog) {
        CompletableFuture<Boolean> gatewayFuture = CompletableFuture.supplyAsync(() -> {
            try { return isReachable(statusLog.getDefaultGateway()); } catch (Exception e) { return false; }
        }, pingExecutor);
        CompletableFuture<Boolean> ipFuture = CompletableFuture.supplyAsync(() -> {
            try { return isReachable(statusLog.getIpAddress()); } catch (Exception e) { return false; }
        }, pingExecutor);

        try {
            Boolean checkGateway = gatewayFuture.get(2, TimeUnit.SECONDS);
            Boolean checkIP = ipFuture.get(2, TimeUnit.SECONDS);
            siteOnlineStatusLogRepositoryCustomImpl.updatePingGateway(statusLog.getId(), checkGateway, true);
            siteOnlineStatusLogRepositoryCustomImpl.updatePingIP(statusLog.getId(), checkIP, true);
            if (checkIP) {
                processLogOfflineCallHealthCheck(statusLog);
            } else {
                siteOnlineStatusLogRepositoryCustomImpl.updateCallHelpcheck(statusLog.getId(), false, true, null);
            }
        } catch (Exception e) {
            log.error("Error in processOfflineLogParallel for site: {} | {}", statusLog.getSiteCode(), e.getMessage());
        }
    }

    private void processOnlineLogParallel(SiteOnlineStatusLog statusLog) {
        CompletableFuture<Boolean> gatewayFuture = CompletableFuture.supplyAsync(() -> {
            try { return isReachable(statusLog.getDefaultGateway()); } catch (Exception e) { return false; }
        }, pingExecutor);
        CompletableFuture<Boolean> ipFuture = CompletableFuture.supplyAsync(() -> {
            try { return isReachable(statusLog.getIpAddress()); } catch (Exception e) { return false; }
        }, pingExecutor);

        try {
            Boolean checkGateway = gatewayFuture.get(2, TimeUnit.SECONDS);
            Boolean checkIP = ipFuture.get(2, TimeUnit.SECONDS);
            siteOnlineStatusLogRepositoryCustomImpl.updatePingGateway(statusLog.getId(), checkGateway, true);
            siteOnlineStatusLogRepositoryCustomImpl.updatePingIP(statusLog.getId(), checkIP, true);
            if (checkIP) {
                processLogOfflineCallHealthCheck(statusLog);
            } else {
                siteOnlineStatusLogRepositoryCustomImpl.updateCallHelpcheck(statusLog.getId(), false, true, null);
            }
        } catch (Exception e) {
            log.error("Error in processOnlineLogParallel for site: {} | {}", statusLog.getSiteCode(), e.getMessage());
        }
    }

    private void processLogOfflineCallHealthCheck(SiteOnlineStatusLog statusLog) {
        try {
            Pair<Boolean, String> result = callHealthCheck(statusLog);
            siteOnlineStatusLogRepositoryCustomImpl.updateCallHelpcheck(
                    statusLog.getId(), result.getLeft(), true, result.getRight());
        } catch (Exception ex) {
            log.error("Error in processLogOfflineCallHealthCheck for site: {}", statusLog.getSiteCode(), ex);
        }
    }

    public Pair<Boolean, String> callHealthCheck(SiteOnlineStatusLog statusLog) {
        try {
            Integer port = statusLog.getPort();
            String ip = statusLog.getIpAddress();
            if (ip == null || port == null || port == 0) {
                return Pair.of(false, "No Connect");
            }

            String targetUrl = "http://" + ip + ":" + port + AppConstants.HEALTHCHECK_URL;
            String apikey_for_device = env.getProperty("apikey_for_device");
            String body = "{\"site_code\": \"" + statusLog.getSiteCode() + "\", \"cabinet_code\":\""
                    + statusLog.getSiteCode() + "-1\"}";

            java.net.URL url = new java.net.URL(targetUrl);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(3000);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("apiKey", apikey_for_device);
            conn.setDoOutput(true);

            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                try (java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream(),
                                java.nio.charset.StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    return Pair.of(true, sb.toString());
                }
            }
            conn.disconnect();
            return Pair.of(false, "No Connect");
        } catch (Exception e) {
            return Pair.of(false, "No Connect");
        }
    }

    public boolean isReachable(String ipAddress) {
        try {
            InetAddress address = InetAddress.getByName(ipAddress);
            return address.isReachable(pingTimeoutMs);
        } catch (IOException e) {
            return false;
        }
    }

    public boolean isHostReachable(String ip, int port, int timeout) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), timeout);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // ─── Async variants (ใช้ใน @Async context) ────────────────────────────────

    @Async("proveOfflineTaskExecutor")
    public CompletableFuture<Void> processLogOfflinePingIPAsync(SiteOnlineStatusLog statusLog) {
        try {
            Boolean checkIP = isReachable(statusLog.getIpAddress());
            siteOnlineStatusLogRepositoryCustomImpl.updatePingIP(statusLog.getId(), checkIP, true);
        } catch (Exception ex) {
            log.error("Error in processLogOfflinePingIPAsync for site: {}", statusLog.getSiteCode(), ex);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Async("proveOfflineTaskExecutor")
    public CompletableFuture<Void> processLogOfflinePingGatewayAsync(SiteOnlineStatusLog statusLog) {
        try {
            Boolean checkGateway = isReachable(statusLog.getDefaultGateway());
            siteOnlineStatusLogRepositoryCustomImpl.updatePingGateway(statusLog.getId(), checkGateway, true);
        } catch (Exception ex) {
            log.error("Error in processLogOfflinePingGatewayAsync for site: {}", statusLog.getSiteCode(), ex);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Async("proveOfflineTaskExecutor")
    public CompletableFuture<Void> processLogOfflineCallHealthCheckAsync(SiteOnlineStatusLog statusLog) {
        try {
            Pair<Boolean, String> result = callHealthCheck(statusLog);
            siteOnlineStatusLogRepositoryCustomImpl.updateCallHelpcheck(
                    statusLog.getId(), result.getLeft(), true, result.getRight());
        } catch (Exception ex) {
            log.error("Error in processLogOfflineCallHealthCheckAsync for site: {}", statusLog.getSiteCode(), ex);
        }
        return CompletableFuture.completedFuture(null);
    }

    // ─── Guard ─────────────────────────────────────────────────────────────────

    // snapshot ต้องทำครั้งเดียว → Leader only
    private boolean isEligible() {
        String site_scheduler = env.getProperty("app.site_scheduler", "no").trim();
        if (site_scheduler.equals("no")) return false;
        if (!schedulerLockService.isLeader()) return false;
        return true;
    }

    // เรียกจากปุ่มในหน้ารายงาน — on-demand healthcheck วันนี้
    public void runHealthCheckNow() {
        LocalTime period = parsePeriodFromCron(env.getProperty("app.site_status_corn", "0 0 4 * * *"));
        log.info("On-demand health check triggered for period={}", period);
        checkHealthCheckInternal(period);
    }

    // ping กระจายทุก node → เช็ค app.ping_enabled แยกจาก site_scheduler
    private boolean isPingEligible() {
        String pingEnabled = env.getProperty("app.ping_enabled", "no").trim();
        return pingEnabled.equals("yes");
    }

    // health check กระจายทุก node → เช็ค app.health_check_enabled แยกต่างหาก
    private boolean isHealthCheckEligible() {
        String hcEnabled = env.getProperty("app.health_check_enabled", "no").trim();
        return hcEnabled.equals("yes");
    }
}
