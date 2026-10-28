package dev.niceone.smartsiteappserver.scheduler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import dev.niceone.smartsiteappserver.elastic.repository.ActivityLogRepository;
import dev.niceone.smartsiteappserver.model.*;
import dev.niceone.smartsiteappserver.model.constant.AlarmTypeCodeEnum;
import dev.niceone.smartsiteappserver.model.constant.StatusCodeEnum;
import dev.niceone.smartsiteappserver.payload.request.SiteKeyRequest;
import dev.niceone.smartsiteappserver.repository.*;
import dev.niceone.smartsiteappserver.service.AlarmLogCasService;
import dev.niceone.smartsiteappserver.service.AlarmLogService;
import dev.niceone.smartsiteappserver.service.AlarmSubmit;
import dev.niceone.smartsiteappserver.specification.SearchOperation;
import dev.niceone.smartsiteappserver.specification.SiteSpecification;
import dev.niceone.smartsiteappserver.specification.SpecSearchCriteria;
import dev.niceone.smartsiteappserver.utils.AppConstants;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import dev.niceone.smartsiteappserver.service.SchedulerConfigService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.client.reactive.ReactorResourceFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.resources.LoopResources;
import reactor.netty.tcp.TcpClient;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static dev.niceone.smartsiteappserver.specification.SiteSpecification.longTimeUpdateKeyGenerate;
import static dev.niceone.smartsiteappserver.specification.SiteSpecification.neverUpdateKeyGenerate;

@Slf4j
@EnableAsync
@Component
@DependsOn({"schedulerLockInitializer", "schedulerConfigInitializer", "onsiteCheckLogInitializer"})
public class SiteScheduler {
    @Autowired
    private Environment env;
    
    @Autowired
    private SchedulerConfigService schedulerConfigService;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private AlarmLogRepository alarmLogRepository;

    @Autowired
    private AlarmSubmit alarmSubmit;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private CabinetRepository cabinetRepository;

    @Value("${app.site_lost_contact_threshold_sec}")
    private int site_lost_contact_threshold_sec;

    @Value("${app.gen_emergency_key_threshold_sec}")
    private int gen_emergency_key_threshold_sec;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private AlarmLogService alarmLogService;

    @Autowired
    private AlarmLogCasService alarmLogCasService;

    @Autowired
    private SiteOnlineStatusLogRepository siteOnlineStatusLogRepository;

    @Autowired
    private SiteOnlineStatusLogRepositoryCustomImpl siteOnlineStatusLogRepositoryCustomImpl ;

    @Autowired
    private dev.niceone.smartsiteappserver.service.BatchProcessingService batchProcessingService;

    @Autowired
    private TaskScheduler taskScheduler;

    @Autowired
    private dev.niceone.smartsiteappserver.service.SiteDistributionService siteDistributionService;

    @Autowired
    private dev.niceone.smartsiteappserver.service.SimpleCircuitBreakerService circuitBreaker;

    @Autowired
    private dev.niceone.smartsiteappserver.service.AlarmThrottleService alarmThrottleService;

    @Autowired
    private dev.niceone.smartsiteappserver.service.SchedulerLockService schedulerLockService;

    @Autowired
    private DailyReportScheduler dailyReportScheduler;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("pingExecutor")
    private java.util.concurrent.Executor pingExecutor;

    @Value("${app.ping.timeout-ms:500}")
    private int pingTimeoutMs;

    // Circuit breaker keys for emergency key generation
    private static final String SITE_KEY_SERVICE_PREFIX = "site-emergency-key-";
    private static final String TTS_KEY_SERVICE = "tts-emergency-key-service";

    // Dynamic cron configuration storage
    private static final Map<String, String> dynamicCronConfig = new ConcurrentHashMap<>();
    private static final Map<String, java.util.concurrent.ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    // เวลาที่ scheduler พร้อมทำงาน (หลังผ่าน grace period)
    private LocalDateTime schedulerReadyTime;

    // Shared WebClient + Reactor Netty resources for emergency-key delivery.
    // สร้างครั้งเดียวแล้วใช้ซ้ำทุก call — เดิมสร้าง TcpClient/WebClient ใหม่ต่อ site
    // ทำให้ event-loop thread รั่วสะสมจนเกิด OOM "unable to create new native thread"
    private WebClient emergencyKeyWebClient;
    private ReactorResourceFactory emergencyKeyResourceFactory;

    @javax.annotation.PostConstruct
    public void init() {
        String site_scheduler = env.getProperty("app.site_scheduler", "no").trim();
        if (site_scheduler.equals("yes")) {
            schedulerReadyTime = LocalDateTime.now().plusSeconds(site_lost_contact_threshold_sec);
            log.info("Startup grace period active — offline detection resumes at {}", schedulerReadyTime);
            // พยายามยึด lock ทันทีตอน startup
            boolean acquired = schedulerLockService.tryAcquireOrRenew();
            leaderAtLastHeartbeat = acquired;
            log.info("Scheduler lock on startup: {} (nodeId={})", acquired ? "ACQUIRED" : "STANDBY", schedulerLockService.getNodeId());

            // Heartbeat มี thread ของตัวเอง: @Scheduled ทุกตัวใช้ pool เดียวกัน (MessageBroker-*)
            // ถ้างานอื่นยาวหรือรอ DB connection อยู่ heartbeat จะเลื่อนจน lock หมดอายุ
            lockHeartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "scheduler-lock-heartbeat");
                t.setDaemon(true);
                return t;
            });
            lockHeartbeat.scheduleWithFixedDelay(this::renewSchedulerLock, 30, 30, TimeUnit.SECONDS);
        }

        // สร้าง WebClient สำหรับส่ง emergency key ครั้งเดียว (ใช้ซ้ำทุก site)
        this.emergencyKeyWebClient = buildEmergencyKeyWebClient();
        log.info("Emergency-key WebClient initialized (shared Reactor Netty resources)");
    }

    /**
     * สร้าง WebClient ที่ใช้ Reactor Netty resources แบบ fixed/shared (event-loop 2 threads,
     * connection pool 50) — สร้างครั้งเดียวใน @PostConstruct แล้ว reuse
     * แทนการสร้าง event-loop group ใหม่ต่อการส่งแต่ละครั้ง (ต้นเหตุ thread leak → OOM)
     */
    private WebClient buildEmergencyKeyWebClient() {
        TcpClient tcpClient = TcpClient
                .create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .doOnConnected(connection -> {
                    connection.addHandlerLast(new ReadTimeoutHandler(5000, TimeUnit.MILLISECONDS));
                    connection.addHandlerLast(new WriteTimeoutHandler(5000, TimeUnit.MILLISECONDS));
                });

        final String poolName = "emergency-key";
        this.emergencyKeyResourceFactory = new ReactorResourceFactory();
        this.emergencyKeyResourceFactory.setUseGlobalResources(false);
        this.emergencyKeyResourceFactory.setLoopResourcesSupplier(() -> LoopResources.create(poolName, 2, true));
        this.emergencyKeyResourceFactory.setConnectionProviderSupplier(() -> ConnectionProvider.fixed(poolName, 50));
        this.emergencyKeyResourceFactory.afterPropertiesSet();

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(this.emergencyKeyResourceFactory,
                        httpClient -> HttpClient.from(tcpClient)))
                .build();
    }

    // Heartbeat ทุก 30 วินาที — ยึด lock ถ้าเป็น leader, หรือ steal ถ้า leader ตาย
    private ScheduledExecutorService lockHeartbeat;
    // ผลของ heartbeat ครั้งก่อน (ไม่ใช้ isLeader() เพราะ lease อาจหมดไประหว่าง JVM ค้าง
    // ทำให้ไม่เห็นว่าเสีย leadership)
    private volatile boolean leaderAtLastHeartbeat = false;

    void renewSchedulerLock() {
        // ห้ามให้ exception หลุดออกไป: ScheduledExecutorService จะยกเลิกรอบถัดไปทั้งหมดเงียบๆ
        try {
            boolean wasLeader = leaderAtLastHeartbeat;
            boolean isLeaderNow = schedulerLockService.tryAcquireOrRenew();
            leaderAtLastHeartbeat = isLeaderNow;

            if (!wasLeader && isLeaderNow) {
                log.info("This node became scheduler leader (nodeId={})", schedulerLockService.getNodeId());
            } else if (wasLeader && !isLeaderNow) {
                log.warn("This node lost scheduler leadership (nodeId={})", schedulerLockService.getNodeId());
            }
        } catch (Throwable t) {
            log.error("Scheduler lock heartbeat failed (nodeId={}): {}", schedulerLockService.getNodeId(), t.getMessage());
        }
    }

    @javax.annotation.PreDestroy
    public void cleanup() {
        log.info("Cleaning up SiteScheduler resources...");
        if (lockHeartbeat != null) {
            lockHeartbeat.shutdownNow();
        }
        String site_scheduler = env.getProperty("app.site_scheduler", "no").trim();
        if (site_scheduler.equals("yes")) {
            schedulerLockService.releaseLock();
        }

        // ปล่อย Reactor Netty resources ของ emergency-key WebClient
        if (emergencyKeyResourceFactory != null) {
            try {
                emergencyKeyResourceFactory.destroy();
            } catch (Exception e) {
                log.warn("Failed to dispose emergency-key WebClient resources: {}", e.getMessage());
            }
        }

        // Cancel all scheduled tasks
        scheduledTasks.values().forEach(task -> {
            if (task != null && !task.isDone()) {
                task.cancel(false);
            }
        });
        scheduledTasks.clear();
        
        // Clear dynamic config
        dynamicCronConfig.clear();
        
        log.info("SiteScheduler cleanup completed");
    }
    
    static {
        // Initialize with default values from config
        dynamicCronConfig.put("site_status_corn", "0 0 6 * * *");
        dynamicCronConfig.put("offline_check_corn", "0 15 6 * * *");
        dynamicCronConfig.put("online_check_corn", "0 20 6 * * *");
    }
    
    // Methods for dynamic cron management
    public void updateCronExpression(String taskName, String cronExpression) {
        dynamicCronConfig.put(taskName, cronExpression);
        log.info("Updated cron expression for {}: {}", taskName, cronExpression);
        
        // Reschedule the task with new cron expression
        rescheduleTask(taskName, cronExpression);
    }
    
    private void rescheduleTask(String taskName, String cronExpression) {
        // Cancel existing scheduled task
        java.util.concurrent.ScheduledFuture<?> existingTask = scheduledTasks.get(taskName);
        if (existingTask != null) {
            existingTask.cancel(false);
            log.info("Cancelled existing scheduled task: {}", taskName);
        }
        
        // Schedule new task with updated cron expression
        Runnable task = getTaskByName(taskName);
        if (task != null) {
            java.util.concurrent.ScheduledFuture<?> scheduledTask = 
                taskScheduler.schedule(task, new CronTrigger(cronExpression));
            scheduledTasks.put(taskName, scheduledTask);
            log.info("Rescheduled task {} with new cron: {}", taskName, cronExpression);
        }
    }
    
    private Runnable getTaskByName(String taskName) {
        switch (taskName.toLowerCase()) {
            case "site_status_corn":
                return dailyReportScheduler::insertMorningStatus;
            case "offline_check_corn":
                return dailyReportScheduler::checkProveIsOffline;
            case "online_check_corn":
                return dailyReportScheduler::checkProveIsOnline;
            default:
                log.warn("Unknown task name for scheduling: {}", taskName);
                return null;
        }
    }
    
    public String getCronExpression(String taskName) {
        return dynamicCronConfig.getOrDefault(taskName, env.getProperty("app." + taskName));
    }
    
    public Map<String, String> getAllCronExpressions() {
        return new HashMap<>(dynamicCronConfig);
    }

    @Scheduled(cron = "15 */2 * * * *")  // second, minute, hour, day of month, month, day of week
    public void generateEmergencyKey() throws ParseException {
        String site_scheduler = env.getProperty("app.site_scheduler");
        site_scheduler = site_scheduler.trim();
        if (site_scheduler.equals("no")) {
            return;
        }
        if (!schedulerLockService.isLeader()) return;

        log.info("start generateEmergencyKey");
        Instant start = Instant.now();

        // Step 1: Reset all flags using single batch update (1 transaction)
        resetEmergencyKeyFlags();

        // Step 2: Get sites that need new keys
        SiteSpecification notGeneratingSpec =
                new SiteSpecification(new SpecSearchCriteria(
                        "keyGenerating", SearchOperation.EQUALITY, false));
        SiteSpecification activeSpec =
                new SiteSpecification(new SpecSearchCriteria(
                        "active", SearchOperation.EQUALITY, true));
        SiteSpecification onlineSpec =
                new SiteSpecification(new SpecSearchCriteria(
                        "status", SearchOperation.EQUALITY, StatusCodeEnum.STATUS_UP));
        List<Site> siteCanGens = siteRepository.findAll(
                (longTimeUpdateKeyGenerate(gen_emergency_key_threshold_sec).or(neverUpdateKeyGenerate()))
                        .and(notGeneratingSpec)
                        .and(activeSpec)
                        .and(onlineSpec));

        log.info("Found {} sites needing emergency key generation", siteCanGens.size());

        // Step 3: Mark sites as generating using batch update (1 transaction)
        if (!siteCanGens.isEmpty()) {
            markSitesAsGenerating(siteCanGens);
        }

        // Step 4: Process sites asynchronously (no database calls here)
        AtomicInteger successCount = new AtomicInteger(0);
        siteCanGens.forEach(site -> {
            try {
                // Generate and send key asynchronously (separate transaction)
                processEmergencyKeyForSiteAsync(site, successCount);
            } catch (Exception e) {
                log.error("Error processing emergency key for site: {} | Error: {}",
                         site.getCode(), e.getMessage());
            }
        });

        Instant end = Instant.now();
        Duration interval = Duration.between(start, end);
        log.info("end generateEmergencyKey in {} seconds, processing {} sites",
                interval.getSeconds(), siteCanGens.size());
    }

    /**
     * Step 1: Reset flags for all online sites (Single transaction)
     */
    @Transactional
    private void resetEmergencyKeyFlags() {
        List<Site> allSites = siteRepository.findByActiveAndStatus(true, StatusCodeEnum.STATUS_UP);
        if (!allSites.isEmpty()) {
            allSites.forEach(site -> {
                site.setKeyGenerating(false);
                site.setEmerg_key_success(false);
            });
            siteRepository.saveAll(allSites);
            log.info("Reset emergency key flags for {} sites", allSites.size());
        }
    }

    /**
     * Step 2: Mark sites as generating (Single transaction)
     */
    @Transactional
    private void markSitesAsGenerating(List<Site> sites) {
        LocalDateTime now = LocalDateTime.now();
        sites.forEach(site -> {
            site.setKeyGenerating(true);
            site.setLastKeyGenerate(now);
        });
        siteRepository.saveAll(sites);
        log.info("Marked {} sites as generating emergency keys", sites.size());
    }

    /**
     * Step 3: Process each site asynchronously (Separate transaction per site)
     * This prevents connection leak by completing transactions before HTTP calls
     * Uses Circuit Breaker to prevent waste of resources on failing sites/services
     */
    @Async
    private void processEmergencyKeyForSiteAsync(Site site, AtomicInteger successCount) {
        String apiKey = env.getProperty("apikey_for_device");
        String siteServiceKey = SITE_KEY_SERVICE_PREFIX + site.getCode();

        // Check circuit breaker before processing
        if (circuitBreaker.isOpen(siteServiceKey)) {
            log.warn("⚠️ Circuit OPEN for site: {} | Skipping emergency key generation", site.getCode());
            updateSiteKeySuccess(site, null, false, false);
            return;
        }

        // Generate key (no database call)
        String siteKey = null;
        try {
            siteKey = generateSiteKey(site);
        } catch (Exception e) {
            log.error("Failed to generate key for site: {} | Error: {}", site.getCode(), e.getMessage());
            circuitBreaker.recordFailure(siteServiceKey);
            return;
        }

        if (siteKey == null) {
            log.info("Skip site emergKey (null key): {}", site.getCode());
            circuitBreaker.recordFailure(siteServiceKey);
            return;
        }

        String finalSiteKey = siteKey;
        SiteKeyRequest siteKeyRequest = new SiteKeyRequest();
        siteKeyRequest.setEmerg_key(finalSiteKey);

        // Send key to site via HTTP
        String targetUrl = "http://" + site.getIp_address() + ":" + site.getPort() + "/" + AppConstants.SITE_EMERGENCY_KEY;
        log.info("Sending emergKey to site: {} | URL: {}", site.getCode(), targetUrl);

        // ใช้ WebClient ที่ share ไว้ (สร้างครั้งเดียวใน init) — ไม่สร้างใหม่ต่อ call อีกต่อไป
        WebClient client = this.emergencyKeyWebClient;

        client.post()
                .uri(targetUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .header("apiKey", apiKey)
                .body(Mono.just(siteKeyRequest), SiteKeyRequest.class)
                .exchange()
                .timeout(Duration.ofSeconds(10))
                .subscribe(
                    response -> {
                        // Success callback
                        HttpStatus statusCode = response.statusCode();
                        log.info("Response from site: {} | Status: {}", site.getCode(), statusCode);

                        if (statusCode.is2xxSuccessful()) {
                            // Record success in circuit breaker
                            circuitBreaker.recordSuccess(siteServiceKey);

                            // Send to TTS (with timeout control and circuit breaker)
                            boolean ttsSuccess = false;
                            try {
                                ttsSuccess = submitEmergencyKeyWithCircuitBreaker(site, finalSiteKey);
                            } catch (Exception e) {
                                log.error("TTS submission failed for site: {} | Error: {}",
                                         site.getCode(), e.getMessage());
                            }

                            // Update database (NEW TRANSACTION)
                            updateSiteKeySuccess(site, finalSiteKey, true, ttsSuccess);

                            // Announce via WebSocket
                            wsAnnounceSubmitKey(site, finalSiteKey,
                                              new AtomicReference<>(true),
                                              new AtomicReference<>(ttsSuccess));

                            successCount.incrementAndGet();
                        } else {
                            // Record failure for non-2xx responses
                            log.warn("Failed to send key to site: {} | Status: {}",
                                   site.getCode(), statusCode);
                            circuitBreaker.recordFailure(siteServiceKey);
                            updateSiteKeySuccess(site, finalSiteKey, false, false);
                        }
                    },
                    error -> {
                        // Error callback - record failure
                        log.error("Network error sending key to site: {} | Error: {}",
                                site.getCode(), error.getMessage());
                        circuitBreaker.recordFailure(siteServiceKey);
                        updateSiteKeySuccess(site, finalSiteKey, false, false);

                        wsAnnounceSubmitKey(site, finalSiteKey,
                                          new AtomicReference<>(false),
                                          new AtomicReference<>(false));
                    }
                );
    }

    /**
     * Update site with key result (Separate transaction)
     */
    @Transactional
    private void updateSiteKeySuccess(Site site, String emergencyKey,
                                     boolean piSuccess, boolean ttsSuccess) {
        // Refresh site from database to avoid stale data
        Site freshSite = siteRepository.findById(site.getId()).orElse(null);
        if (freshSite == null) {
            log.error("Site not found for update: {}", site.getCode());
            return;
        }

        if (piSuccess) {
            freshSite.setSite_key(emergencyKey);
            freshSite.setEmerg_key_success(true);
            freshSite.setLastKeyUpdate(LocalDateTime.now());
        }

        freshSite.setKeyGenerating(false);
        siteRepository.save(freshSite);

        log.info("Updated site: {} | PI Success: {} | TTS Success: {}",
                site.getCode(), piSuccess, ttsSuccess);
    }

    /**
     * Submit emergency key to TTS with circuit breaker and timeout control
     */
    private boolean submitEmergencyKeyWithCircuitBreaker(Site site, String emerg_key) {
        // Check circuit breaker before calling TTS
        if (circuitBreaker.isOpen(TTS_KEY_SERVICE)) {
            log.warn("⚠️ TTS Circuit OPEN | Skipping emergency key submission for site: {}", site.getCode());
            return false;
        }

        try {
            // Set timeout before call
            Unirest.setTimeouts(3000, 5000); // Connection: 3s, Socket: 5s

            boolean result = submitEmergencyKey(site, emerg_key);

            // Reset timeout to default
            Unirest.setTimeouts(5000, 10000);

            // Record result in circuit breaker
            if (result) {
                circuitBreaker.recordSuccess(TTS_KEY_SERVICE);
                log.debug("✓ TTS submission successful for site: {}", site.getCode());
            } else {
                circuitBreaker.recordFailure(TTS_KEY_SERVICE);
                log.warn("✗ TTS submission failed for site: {} | ResultCode != 0", site.getCode());
            }

            return result;
        } catch (Exception e) {
            log.error("TTS submission error for site: {} | Error: {}",
                     site.getCode(), e.getMessage());

            // Reset timeout to default
            Unirest.setTimeouts(5000, 10000);

            // Record failure in circuit breaker
            circuitBreaker.recordFailure(TTS_KEY_SERVICE);

            return false;
        }
    }

    /**
     * Submit emergency key to TTS with timeout control (legacy method for backward compatibility)
     * @deprecated Use submitEmergencyKeyWithCircuitBreaker instead
     */
    @Deprecated
    private boolean submitEmergencyKeyWithTimeout(Site site, String emerg_key) {
        return submitEmergencyKeyWithCircuitBreaker(site, emerg_key);
    }

    private void wsAnnounceSubmitKey(Site site, String emerg_key,
                                     AtomicReference<Boolean> piSuccess,
                                     AtomicReference<Boolean> ttsSuccess) {
        JSONObject wsJson = new JSONObject();
        wsJson
                .put("siteCode", site.getCode())
                .put("lastKeyUpdate", site.getLastKeyUpdate())
                .put("piSuccess", piSuccess)
                .put("ttsSuccess", ttsSuccess)
                .put("emergKey", emerg_key);
        messagingTemplate.convertAndSend("/topic/emergency_key_report", wsJson.toString());
    }

    private String generateSiteKey(Site site) throws NoSuchAlgorithmException, NoSuchPaddingException,
            InvalidKeyException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException {
        SecureRandom secureRandom = new SecureRandom();
        byte[] key = new byte[16];
        secureRandom.nextBytes(key);
        SecretKey secretKey = new SecretKeySpec(key, 0, key.length, "AES");

        byte[] iv = new byte[12]; //NEVER REUSE THIS IV WITH SAME KEY
        secureRandom.nextBytes(iv);

        final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv); //128 bit auth tag length
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

        String msg = site.getCode();
        byte[] bytes = cipher.doFinal(msg.getBytes());
        //21-bytes key

        return bytes.toString();
    }
    private Boolean submitEmergencyKey(Site site, String emerg_key) throws IOException, JSONException {
        // to TTS
        Boolean result = false;
        JSONArray cabinetList = new JSONArray();
        // beware cabinets are LAZY fetch now.
        // List<Cabinet> cabinets = site.getCabinets();
        List<Cabinet> cabinets = cabinetRepository.findBySiteAndActiveIsTrue(site);
        cabinets.forEach(cabinet -> {
            if (cabinet.getActive()) {
                cabinetList.put(new JSONObject()
                        .put("cabinetCode", cabinet.getCode())
                        .put("isCabinetActive", StringUtils.capitalize("" + cabinet.getActive()))
                        .put("emergencyKey", emerg_key)
                        .put("deviceName", "SmartSite_" + site.getCode())
                );
            }
        });
        JSONObject siteJson = new JSONObject();
        siteJson
                .put("siteCode", site.getCode())
                .put("isSiteActive", StringUtils.capitalize(""+site.getActive()))
                .put("cabinetList", cabinetList);
        log.debug("---------- submitEmergencyKey "+siteJson);

        JSONArray outputArray = new JSONArray();
        outputArray.put(siteJson);

        String ttsm_ip = env.getProperty("ttsm_ip");
        String ttsm_port = env.getProperty("ttsm_port");
        String endpoint = env.getProperty("ttsm_endpoint_update_emerg_key");
        String apikey_by_ttsm = env.getProperty("apikey_by_ttsm");
        String targetUrl = "" + ttsm_ip + ":" + ttsm_port + "/" + endpoint;

        ActivityLog mylog = new ActivityLog("submitEmergKeyToTTS", "CRON",
                outputArray.toString(),
                "INFO", "OK");
//        activityLogRepository.save(mylog);

        try {
            log.info(" " + site.getCode() + " send emerg to TTS "+targetUrl);
            log.info(" "+outputArray);
            HttpResponse<String> response = Unirest.post(targetUrl)
                    .header("Content-Type", "application/json")
                    .header("apiKey", apikey_by_ttsm)
                    .body(outputArray.toString())
                    .asString();

            // Retrieve the parsed JSONObject from the response
            String responseString = response.getBody();
            log.info(" TTS responseString: "+responseString);
            JSONObject jsonResp = new JSONObject(responseString);
            String resultCode = String.valueOf( jsonResp.get("resultCode") );
            String resultDescription = String.valueOf( jsonResp.get("resultCode") );
            if (resultCode.equals("0")) {
                result = true;
                log.info("submitEmergKeyToTTS result: "+resultDescription);
            } else {
                log.info("submitEmergKeyToTTS result: "+resultDescription);
            }
//            String errMsg = String.valueOf( jsonResp.get("err_msg") );
//            log.info("msg "+errMsg);
        } catch (UnirestException e) {
            // TODO Auto-generated catch block
//            e.printStackTrace();
            String errMsg = "Failed submitEmergKeyToTTS: " + e.getMessage();
            log.error("submitEmergKeyToTTS err "+errMsg);
        }
        return result;
    }

    @Scheduled(cron = "0 */1 * * * *")
    public void checkSiteStatus() throws IOException {
        String site_scheduler = env.getProperty("app.site_scheduler");
        site_scheduler = site_scheduler.trim();
        if (site_scheduler.equals("no")) {
            return ;
        }
        if (!schedulerLockService.isLeader()) return;

        log.info("az log start checkSiteStatus");
        Instant start = Instant.now();
        ActivityLog initLog = new ActivityLog("checkSiteStatus", "CRON", "Init check site status Started",
                "INFO", "OK");
//        activityLogRepository.save(initLog);

        // Grace period หลัง startup — รอให้ sites มีเวลา re-report ก่อน mark offline
        if (schedulerReadyTime != null && LocalDateTime.now().isBefore(schedulerReadyTime)) {
            long secondsLeft = java.time.temporal.ChronoUnit.SECONDS.between(LocalDateTime.now(), schedulerReadyTime);
            log.info("Startup grace period active — offline detection resumes in {} seconds", secondsLeft);
            return;
        }

//        OffsetDateTime offsetTime = OffsetDateTime.now().plusSeconds(site_lost_contact_threshold_sec);
        LocalDateTime offsetTime = LocalDateTime.now().minusSeconds(site_lost_contact_threshold_sec);
//        log.info("offsetTime: "+offsetTime);

        log.info("az log get offline start");
        List<Site> offlineSites = siteRepository.findByActiveAndIsInstallAndReportStatusAtLessThan(
                true, true, offsetTime);
        List<Site> _notSendReport = offlineSites.stream().filter(x -> x.getStatus() != StatusCodeEnum.STATUS_DOWN).collect(Collectors.toList());
        log.info("az log get offline end result->" + _notSendReport.stream().count());
        log.info("az log update offline start");
        int retUpdate = siteRepository.updateStatusAndTimestamps(offsetTime);
        log.info("az log update offline end result->" + retUpdate);
        ProcessSetCommunicationLostAndCallAwake(_notSendReport);
//        List<Site> onlineSites = siteRepository.findByActiveAndIsInstallAndReportStatusAtGreaterThan(
//                true, true, offsetTime);
//        List<AlarmLog> unresolvedAlarm = alarmLogService.getAllByAlarmType(AlarmTypeCodeEnum.ALARM_TYPE_COMM_LOST_SERV, false);
        List<Site> updateSites = new ArrayList<>();
//เริ่ม function
//        offlineSites.stream().filter(x -> x.getStatus() != StatusCodeEnum.STATUS_DOWN).forEach(site -> {
//            log.info("az log offline " + site.getCode() + " Offline");
//            site.setStatus(StatusCodeEnum.STATUS_DOWN);
//            LocalDateTime updateOld = site.getUpdatedAt();
//            site.setReportStatusAt(updateOld);
//            site.setUpdatedAt(LocalDateTime.now());
//
//            siteRepository.save(site);
//            SetCreateAlarmCommunicationLost(site);
//            processCallAwakenSite(site);
//            log.info("az log offline update success" + site.getCode() + " Offline");
//        });
//ปิด function

//        offlineSites.stream().forEach(site -> {
//            if (site.getStatus() != StatusCodeEnum.STATUS_DOWN) {
//                log.info("az offline " + site.getCode() + " Offline");
//                site.setStatus(StatusCodeEnum.STATUS_DOWN);
//                site.setUpdatedAt(LocalDateTime.now());
//                siteRepository.save(site);
//                log.info("az offline update success" + site.getCode() + " Offline");
//                //updateSites.add(site);
//
////                AlarmLog existAlarm = unresolvedAlarm.stream()
////                        .filter(alarmLog -> alarmLog.getSite().getId() == site.getId())
////                        .findFirst()
////                        .orElse(null);
////                // To reduce n+1 problem
////                if (existAlarm == null) {
                    //alarmLogCasService.getOrCreateAlarmLog(AlarmTypeCodeEnum.ALARM_TYPE_COMM_LOST_SERV, site, null, null, null);
////                }
//            }
//        });
//
//        onlineSites.stream().forEach(site -> {
//            if (site.getStatus() != StatusCodeEnum.STATUS_UP) {
////                log.info(site.getCode() + " Online");
//                site.setStatus(StatusCodeEnum.STATUS_UP);
//                site.setUpdatedAt(LocalDateTime.now());
//                updateSites.add(site);
//            }
//
//            //resolve alarm
//            unresolvedAlarm.stream()
//                .filter(alarmLog -> alarmLog.getSite().getId() == site.getId())
//                .forEach(alarmLog -> {
////                    log.info("resove: "+site.getId());
//                    alarmLog.setResolveDate(LocalDateTime.now());
//                    alarmLog.setWriteToFile(false);
//                    alarmLogRepository.save(alarmLog);
//                });
////                wsAnnounceCheckSite(site, true); //report to home frontend
//        });

        // query no report site and not offline then mark all offline
//        List<Site> noReportSites = siteRepository.findByActiveAndIsInstallAndReportStatusAtIsNullAndStatusIsNot(
//                true, true, StatusCodeEnum.STATUS_DOWN);
//        noReportSites.stream().forEach(site -> {
////            log.info(site.getCode() + " No report");
//            site.setStatus(StatusCodeEnum.STATUS_DOWN);
//            site.setUpdatedAt(LocalDateTime.now());
//            updateSites.add(site);
//        });

//        siteRepository.saveAll(updateSites);
//        log.info("az offline save complete");
        // site
//        SiteSpecification activeSiteSpec =
//                new SiteSpecification(new SpecSearchCriteria("active", SearchOperation.EQUALITY, true));
//        SiteSpecification installSiteSpec =
//                new SiteSpecification(new SpecSearchCriteria("isInstall", SearchOperation.EQUALITY, true));
//        List<Site> siteList = siteRepository.findAll(activeSiteSpec.and(installSiteSpec));
//        List<AlarmLog> unresolvedAlarm = alarmLogService.getAllByAlarmType(AlarmTypeCodeEnum.ALARM_TYPE_COMM_LOST_SERV, false);
//
//        List<Site> updateSites = new ArrayList<>();
//
//        siteList.stream().forEach(site -> {
//            // Site Down
//            long diffSec = 0;
//            if (site.getReportStatusAt() != null) {
//                diffSec = ChronoUnit.SECONDS.between(site.getReportStatusAt(), LocalDateTime.now());
//
//            }
//            if (site.getReportStatusAt() == null || diffSec > site_lost_contact_threshold_sec) {
//                if (site.getStatus() != StatusCodeEnum.STATUS_DOWN) {
//                    log.info(site.getCode() + " Down");
//                    site.setStatus(StatusCodeEnum.STATUS_DOWN);
//                }
//                site.setUpdatedAt(LocalDateTime.now());
//
//                AlarmLog existAlarm = unresolvedAlarm.stream()
//                        .filter(alarmLog -> alarmLog.getSite().getId() == site.getId())
//                        .findFirst()
//                        .orElse(null);
//                // To reduce n+1 problem
//                if (existAlarm == null) {
//                    alarmLogService.getOrCreateAlarmLog(AlarmTypeCodeEnum.ALARM_TYPE_COMM_LOST_SERV, site, null, null, null);
//                }
//                updateSites.add(site);
////                wsAnnounceCheckSite(site, false); //report to home frontend
//            }
//            // Site Up
//            else {
//                if (site.getStatus() != StatusCodeEnum.STATUS_UP) {
//                    log.info(site.getCode() + " Up");
//                    site.setStatus(StatusCodeEnum.STATUS_UP);
//                }
//                site.setUpdatedAt(LocalDateTime.now());
//                //resolve alarm
//                unresolvedAlarm.stream()
//                    .filter(alarmLog -> alarmLog.getSite().getId() == site.getId())
//                    .forEach(alarmLog -> {
//                        log.info("resove: "+site.getId());
//                        alarmLog.setResolveDate(LocalDateTime.now());
//                        alarmLog.setWriteToFile(false);
//                        alarmLogRepository.save(alarmLog);
//                    });
//
//                updateSites.add(site);
////                wsAnnounceCheckSite(site, true); //report to home frontend
//            }
//        });
//        siteRepository.saveAll(updateSites);

//        alarmLogRepository.saveAll(createAlarmLogs);
//        ActivityLog endLog = new ActivityLog("checkSiteStatus", "CRON", "Finished check site status",
//                "INFO", "OK");
//        activityLogRepository.save(endLog);

//        messagingTemplate.convertAndSend("/topic/checkSiteStatus", "ok");

        Instant end = Instant.now();
        Duration interval = Duration.between(start, end);
        log.info("az log end checkSiteStatus in "+interval.getSeconds()+" seconds");
    }

    @Async
    void wsAnnounceCheckSite(Site site, Boolean isSiteUp) {
        JSONObject wsJson = new JSONObject();
        wsJson
                .put("siteCode", site.getCode())
                .put("updatedAt", site.getUpdatedAt())
                .put("isSiteUp", isSiteUp);
        messagingTemplate.convertAndSend("/topic/checksite_report", wsJson.toString());
    }

    @Async
    @Scheduled(cron = "30 */1 * * * *") // run every minute when seconds=30
    @Transactional
    public void reportAlarmLogToFile() throws IOException {
        String site_scheduler = env.getProperty("app.site_scheduler");
        site_scheduler = site_scheduler.trim();
        if (site_scheduler.equals("no")) {
            return;
        }
        if (!schedulerLockService.isLeader()) return;
        log.info("start reportAlarmLogToFile");
//        ActivityLog initLog = new ActivityLog("reportAlarmLogToFile", "CRON", "ReportAlarmLogToFile Started",
//                "INFO", "OK");
//        activityLogRepository.save(initLog);

        log.info("end reportAlarmLogToFile");
    }

    /**
     * Cleanup old alarm trackers to prevent memory leaks
     * Runs every hour to remove stale trackers (> 1 hour old)
     * Trackers live in each node's memory (app.alarm.notify.mode=legacy), so every node cleans its own.
     */
    @Scheduled(cron = "0 0 * * * *") // Every hour at minute 0
    public void cleanupAlarmTrackers() {
        log.info("Starting alarm tracker cleanup");
        alarmThrottleService.cleanupOldTrackers();
        log.info("Alarm tracker cleanup completed");
    }

    /** Sends the held clear of alarm episodes that have been quiet long enough (AlarmNotifyGate). */
    @Scheduled(cron = "30 * * * * *")
    public void sendDueAlarmClears() {
        String site_scheduler = env.getProperty("app.site_scheduler");
        if (site_scheduler == null || site_scheduler.trim().equals("no")) {
            return;
        }
        if (!schedulerLockService.isLeader()) return;
        try {
            alarmLogCasService.sendDueClears();
        } catch (Exception e) {
            log.warn("sendDueAlarmClears failed: {}", e.getMessage());
        }
    }

    private void ProcessSetCommunicationLostAndCallAwake(List<Site> sites){
        // Use BatchProcessingService for better performance and monitoring
        batchProcessingService.processSmartBatch(sites, site -> {
            SetCreateAlarmCommunicationLost(site);
            processCallAwakenSite(site);
        }, "CommunicationLostAndAwake");
    }

    @Async
    private void SetCreateAlarmCommunicationLost(Site site){
        List<SiteEventLog> unresolvedAlarm = alarmLogCasService.getAllByAlarmType(AlarmTypeCodeEnum.ALARM_TYPE_COMM_LOST_SERV, false,site);
        SiteEventLog existAlarm = null;
        if(unresolvedAlarm != null){
            existAlarm = unresolvedAlarm.stream()
                    .filter(alarmLog -> alarmLog.getSiteId().equals(String.valueOf(site.getId())))
                    .findFirst()
                    .orElse(null);
        }
        // To reduce n+1 problem
        if (existAlarm == null) {
            try {
                log.info("ProcessSetCommunicationLostAndCallAwake ALARM_TYPE_COMM_LOST_SERV " + site.getCode() + " Offline");
                alarmLogCasService.getOrCreateAlarmLog(AlarmTypeCodeEnum.ALARM_TYPE_COMM_LOST_SERV, site.getCode(), null, null, null);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Async
    private void processCallAwakenSite(Site site){

        try {
            cabinetRepository.findBySite(site).forEach((cabinet) -> {
                String ip = site.getIp_address();
                int port = site.getPort();
               // boolean isReachable = isReachable(ip);

//                if(isReachable){
//                    try {
//                        CallAwakenSite(ip, port, cabinet);
//                    } catch (JsonProcessingException e) {
//                        throw new RuntimeException(e);
//                    }
//                }else{
//                    SetCreateAlarmCommunicationLost(site);
//                }
                try {

                    CallAwakenSite(ip, port, cabinet);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            });

        }catch (Exception ex)
        {

        }

    }

    private Map<String, String> CallAwakenSite(String ip, int port, Cabinet cabinet) throws JsonProcessingException {

        String targetUrl = "http://"+ip+":"+port+AppConstants.HEALTHCHECK_URL;
        Site site = siteRepository.findByCode(cabinet.getSite().getCode()).orElse(null);
        HashMap<String, String> result = new HashMap<>();
        String errMsg = "";
        boolean isError = false;
        String apikey_for_device = env.getProperty("apikey_for_device");

        if(site == null){
            result.put("isError", "true");
            result.put("errMsg", "Site not found");
            return result;
        }

        // ✅ Circuit breaker key for this site
        String siteServiceKey = "awaken-site-" + site.getCode();

        // ✅ Check circuit breaker before calling
        if (circuitBreaker.isOpen(siteServiceKey)) {
            log.warn("⚠️ Circuit OPEN for site: {} | Skipping awaken call", site.getCode());
            result.put("isError", "true");
            result.put("errMsg", "Circuit breaker open");
            return result;
        }

        try {
            // ✅ Set timeout: Connection 3s, Socket 5s (max 8 seconds total)
            Unirest.setTimeouts(3000, 5000);

            HttpResponse<String> response = Unirest.post(targetUrl)
                    .header("Content-Type", "application/json")
                    .header("apiKey", apikey_for_device)
                    .body("{\"site_code\": \"" + cabinet.getSite().getCode() + "\", \"cabinet_code\":\""+cabinet.getCode() +"\"}")
                    .asString();

            // Parse response
            String responseString = response.getBody();
            JSONObject jsonResp = new JSONObject(responseString);
            boolean is_success = (boolean) jsonResp.get("is_success");

            // ✅ Record success in circuit breaker
            circuitBreaker.recordSuccess(siteServiceKey);

            // Update site status
            site.setStatus(StatusCodeEnum.STATUS_UP);
            site.setReportStatusAt(LocalDateTime.now());
            site.setUpdatedAt(LocalDateTime.now());

            try {
                siteRepository.save(site);
                log.info("ProcessSetCommunicationLostAndCallAwake CallAwakenSite {} Success", site.getCode());
            } catch (Exception ex) {
                log.error("Failed to save site {} status: {}", site.getCode(), ex.getMessage());
            }

            result.put("isError", "false");
            result.put("errMsg", "");

        } catch (UnirestException e) {
            // ✅ Record failure in circuit breaker
            circuitBreaker.recordFailure(siteServiceKey);

            isError = true;
            errMsg = "Failed to communicate with targeted site: " + ip+":"+port;
            log.warn("ProcessSetCommunicationLostAndCallAwake CallAwakenSite {} Fail: {}",
                     site.getCode(), e.getMessage());

            result.put("isError", "true");
            result.put("errMsg", errMsg);

        } catch (org.json.JSONException e) {
            // ✅ Record failure in circuit breaker
            circuitBreaker.recordFailure(siteServiceKey);

            isError = true;
            errMsg = "Invalid JSON response from site: " + ip+":"+port;
            log.warn("ProcessSetCommunicationLostAndCallAwake CallAwakenSite {} JSON parse error: {}",
                     site.getCode(), e.getMessage());

            result.put("isError", "true");
            result.put("errMsg", errMsg);

        } finally {
            // ✅ Reset timeout to default
            Unirest.setTimeouts(5000, 10000);
        }

        return result;
    }


    // Daily report methods ย้ายไปที่ DailyReportScheduler.java แล้ว

}
