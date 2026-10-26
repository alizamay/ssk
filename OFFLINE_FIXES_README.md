# แผนแก้ไขปัญหา SmartSite (Offline Server)

**จากการวิเคราะห์ Log วันที่ 25 พ.ย. 2025**
- **49,683 errors** ใน 10.65 ชั่วโมง
- **20,849 Communication Lost events** กระทบ 12,212 sites
- **Peak Crisis**: 10:15-10:30 มี 2,964 errors/นาที

---

## 🎯 ปัญหาหลักที่พบ

### 1. HikariCP Connection Pool Exhaustion ⚠️ **CRITICAL**
- **a1ssm01**: 4,459 pool timeouts
- **a2ssm02**: 4,258 pool timeouts
- Pool size 30 น้อยเกินไป

### 2. External Service Down (10.197.73.189:3000)
- **7,472 errors** (15% ของทั้งหมด)
- Connection Refused + Premature Close
- กระทบทั้ง 8 servers

### 3. Server Load Imbalance
- **a2ssm01** รับ load 36.2% (มากกว่าคนอื่น 5 เท่า!)

### 4. Communication Lost Bursts
- **1,345 sites** หายพร้อมกันเวลา 09:53
- **83 burst events** ใน 11 ชั่วโมง

---

## ✅ สิ่งที่แก้ไขแล้ว (ไม่ต้องติดตั้ง library เพิ่ม)

### ไฟล์ที่แก้ไข:

#### 1. `application.properties` ✅
```properties
# เดิม
spring.datasource.hikari.maximumPoolSize=30
spring.datasource.hikari.minimumIdle=5

# ใหม่ (เพิ่ม 3.3 เท่า)
spring.datasource.hikari.maximumPoolSize=100
spring.datasource.hikari.minimumIdle=20
spring.datasource.hikari.registerMbeans=true  # เปิด monitoring
```

**ผลลัพธ์ที่คาดหวัง**:
- ลด HikariPool timeout จาก 8,717 errors → ใกล้ 0
- Connection wait time < 100ms

---

#### 2. `ConnectionPoolMonitorService.java` ✅ **ไฟล์ใหม่**

**ฟีเจอร์**:
- Monitor pool usage ทุก 1 นาที
- Alert เมื่อ utilization > 80%
- Log statistics ทุก 5 นาที (CSV format)
- ตรวจจับ bottleneck (threads waiting for connection)

**ตัวอย่าง log ที่จะเห็น**:
```
✓ Pool Health: Utilization=45.0% Active=45 Idle=55 Total=100/100
⚠️ WARNING: Connection Pool Utilization=85.0% Active=85 Idle=15 Waiting=5
⚠️ CRITICAL: Connection Pool High Utilization for 3 minutes!
```

**ไม่ต้องติดตั้งอะไรเพิ่ม**: ใช้ HikariCP MBean ที่มีอยู่แล้ว

---

#### 3. `SimpleCircuitBreakerService.java` ✅ **ไฟล์ใหม่**

**แก้ปัญหา**: 10.197.73.189:3000 ล่ม → ทำให้เกิด cascade failures

**ฟีเจอร์**:
- Auto-detect service down (fail 5 ครั้งติด → เปิด circuit)
- Block requests ชั่วคราว (60 วินาที)
- Auto recovery (ทดลองส่ง request อีกครั้งหลังพัก)
- In-memory state (ไม่ต้องใช้ Redis)

**States**:
- `CLOSED` - ปกติ ให้ request ผ่าน
- `OPEN` - หยุดส่ง request (service ล่ม)
- `HALF_OPEN` - ทดลองส่งดูว่า service กลับมาหรือยัง

**ไม่ต้องติดตั้ง**: Spring Resilience4j, Netflix Hystrix

---

#### 4. `RetryableHttpService.java` ✅ **ไฟล์ใหม่**

**แก้ปัญหา**: Connection Refused 10,420 errors

**ฟีเจอร์**:
- Retry 3 ครั้ง พร้อม exponential backoff (1s → 2s → 4s)
- รวม circuit breaker เข้าด้วย
- ตรวจจับ retryable errors (network errors, 5xx)
- ไม่ retry client errors (4xx)

**ตัวอย่างการใช้**:
```java
@Autowired
private RetryableHttpService httpService;

String serviceKey = "external-api-10.197.73.189:3000";
String url = "http://10.197.73.189:3000/api/status";

try {
    ResponseEntity<String> response = httpService.getWithRetry(
        serviceKey, restTemplate, url, String.class
    );
    return response.getBody();
} catch (Exception e) {
    // Fallback
    return "Service unavailable";
}
```

**ไม่ต้องติดตั้ง**: Spring Retry, Resilience4j Retry

---

#### 5. `AlarmThrottleService.java` ✅ **ไฟล์ใหม่**

**แก้ปัญหา**: Alarm flooding เมื่อประตู/sensor เสียทำให้ส่ง alarm ซ้ำ ๆ ไปที่ Autin

**สถานการณ์ที่เกิด**:
```
09:00:01 - Site ABC01, UNAUTH_OPENED → CREATE → ส่งไป Autin
09:00:03 - Site ABC01, UNAUTH_OPENED → CLEAR → ส่งไป Autin
09:00:05 - Site ABC01, UNAUTH_OPENED → CREATE → ส่งไป Autin
09:00:07 - Site ABC01, UNAUTH_OPENED → CLEAR → ส่งไป Autin
... (ส่งไปเรื่อย ๆ ทำให้ Autin ล้น!)
```

**ฟีเจอร์**:
- **Flapping Detection** - ตรวจจับเมื่อ site มีการสลับ CREATE/CLEAR เร็ว (3+ events ใน 60 วินาที)
- **Smart Throttling** - ส่ง alarm แรก แล้วหยุดส่งซ้ำภายใน 5 นาที
- **Flapping Suppression** - หยุดส่ง alarm ทั้งหมดเมื่อตรวจพบ flapping จนกว่าจะคงที่
- **Automatic Cleanup** - ลบ tracker เก่า (> 1 ชั่วโมง) ทุกชั่วโมง

**States**:
```
Normal → (3+ events in 60s) → Flapping → (ไม่มี event 3 นาที) → Stabilized → Reset
```

**Configuration**:
- `THROTTLE_WINDOW_SECONDS = 300` (5 นาที - ไม่ส่ง duplicate ภายใน window นี้)
- `FLAPPING_THRESHOLD = 3` (3+ events = flapping)
- `FLAPPING_WINDOW_SECONDS = 60` (ตรวจจับ flapping ภายใน 60 วินาที)
- `STABILIZATION_WINDOW_SECONDS = 180` (รอ 3 นาทีหลัง event สุดท้ายก่อน reset)

**Integration Points**:
- `AlarmLogCasService.java` - เพิ่ม throttle check ก่อนส่ง alarm (4 จุด)
- `SiteScheduler.java` - เพิ่ม cleanup task ทุกชั่วโมง

**ตัวอย่าง Log**:
```
✓ Alarm allowed [ABC01:16] - Not flapping, within throttle window
⊗ Alarm throttled [ABC01:16] - Within throttle window (180s remaining)
🚨 FLAPPING DETECTED [ABC01:16] - 5 events (3C/2R) in 45s - Suppressing alarms
⚠ Alarm SUPPRESSED [ABC01:16] - Flapping detected! Events: 5 (3C/2R) in 45s
📊 Alarm stabilized [ABC01:16] - Flapping ended. Summary: 15 total events (8C/7R) over 120s
♻ Alarm tracker reset [ABC01:16] - Stabilization period complete
```

**ไม่ต้องติดตั้ง**: อะไรเลย - ใช้ ConcurrentHashMap ที่มีใน Java Standard Library

**ดูรายละเอียดเพิ่มเติม**: [ALARM_THROTTLING_README.md](ALARM_THROTTLING_README.md)

---

## 📋 วิธีการ Deploy (Offline Server)

### Step 1: Compile โค้ด
```bash
cd C:\SSK_CODE\ssk
mvnw clean package -DskipTests
```

### Step 2: ตรวจสอบ WAR file
```bash
# ตรวจสอบว่า compile สำเร็จ
ls target/ssws.war
```

### Step 3: Copy ไฟล์ไปยัง Production Server
```bash
# Method 1: USB Drive
copy target\ssws.war E:\deployment\

# Method 2: SCP (ถ้ามี network)
scp target/ssws.war user@production-server:/path/to/deploy/
```

### Step 4: Deploy บน Production
```bash
# Stop application
systemctl stop smartsite

# Backup old version
cp /path/to/old/ssws.war /backup/ssws.war.backup

# Deploy new version
cp ssws.war /path/to/deploy/

# Start application
systemctl start smartsite

# Monitor logs
tail -f /var/log/smartsite/application.log
```

---

## 🔍 การตรวจสอบหลัง Deploy

### 1. เช็ค Connection Pool
```bash
# ดู log ว่า monitor ทำงานหรือไม่
grep "Pool Health" /var/log/smartsite/application.log

# ควรเห็น
✓ Pool Health: Utilization=XX% Active=XX Idle=XX Total=XX/100
```

### 2. เช็ค Circuit Breaker
```bash
# ถ้ามี external service ล่ม จะเห็น
grep "Circuit" /var/log/smartsite/application.log

# ตัวอย่าง
⚠️ Circuit [external-api-10.197.73.189:3000] OPENED - 5 consecutive failures
✓ Circuit [external-api-10.197.73.189:3000] CLOSED - Service recovered
```

### 3. Monitor Pool Statistics
```bash
# Extract pool stats สำหรับวิเคราะห์
grep "POOL_STATS" /var/log/smartsite/application.log > pool_stats.csv

# Format: timestamp,active,idle,total,waiting
# นำไป plot graph ได้
```

---

## 🚀 สิ่งที่ต้องแก้เพิ่มเติม

### 1. แก้ไข generateEmergencyKey() ใน SiteScheduler.java ⚠️ **สำคัญ**

**ปัญหาที่พบ**: Method นี้ส่ง emergency key ไปยัง sites และ TTS แต่ไม่มี retry และ circuit breaker

**ตำแหน่ง**: [SiteScheduler.java:217-388](src/main/java/dev/niceone/smartsiteappserver/scheduler/SiteScheduler.java#L217-L388)

#### ปัญหา 1: WebClient ส่งไปยัง Site ไม่มี Retry (บรรทัด 310-378)

**เดิม**:
```java
Mono<ClientResponse> clientResponse = client
    .post()
    .uri(targetUrl)
    .contentType(MediaType.APPLICATION_JSON)
    .header("apiKey", apiKey)
    .body(Mono.just(siteKeyRequest), SiteKeyRequest.class)
    .exchange().timeout(Duration.ofSeconds(10))
    .doOnSuccess(response -> log.debug("pi response headers: " + response.headers()));

clientResponse.subscribe((response) -> {
    // handle success
}, (ex) -> {
    // handle network error - ไม่มี retry!
    log.debug("emergKey to site fail!! network "+ex.getMessage());
});
```

**ปัญหา**:
- Timeout 10 วินาที แต่ไม่มี retry
- ถ้า site offline ชั่วคราว จะ fail ทันที
- Network hiccup จะทำให้ key generation ล้มเหลว

**แก้ไข**: เพิ่ม retry และ circuit breaker check

```java
@Autowired
private SimpleCircuitBreakerService circuitBreaker;

private static final String SITE_KEY_SERVICE_PREFIX = "site-emergency-key-";

// ใน generateEmergencyKey() method
siteCanGens.stream().map(site -> {
    AtomicReference<Boolean> piSuccess = new AtomicReference<>(false);
    AtomicReference<Boolean> ttsSuccess = new AtomicReference<>(false);

    // ตรวจสอบ circuit breaker ก่อนส่ง
    String siteServiceKey = SITE_KEY_SERVICE_PREFIX + site.getCode();
    if (circuitBreaker.isOpen(siteServiceKey)) {
        log.warn("Circuit OPEN for site {}, skipping emergency key send", site.getCode());
        return site;
    }

    site.setKeyGenerating(true);
    site.setLastKeyGenerate(LocalDateTime.now());
    siteRepository.save(site);

    String siteKey = null;
    try {
        siteKey = generateSiteKey(site);
    } catch (Exception e) {
        e.printStackTrace();
        circuitBreaker.recordFailure(siteServiceKey);
        return site;
    }

    String finalSiteKey = siteKey;
    if (finalSiteKey == null) {
        log.info("skip site emergKey: " + site.getCode());
        return site;
    }

    SiteKeyRequest siteKeyRequest = new SiteKeyRequest();
    siteKeyRequest.setEmerg_key(finalSiteKey);

    String targetUrl = "http://" + site.getIp_address() + ":" + site.getPort() + "/" + AppConstants.SITE_EMERGENCY_KEY;
    log.info("try send emergKey to site!! "+ site.getCode() + " " + targetUrl);

    TcpClient tcpClient = TcpClient
            .create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
            .doOnConnected(connection -> {
                connection.addHandlerLast(new ReadTimeoutHandler(5000, TimeUnit.MILLISECONDS));
                connection.addHandlerLast(new WriteTimeoutHandler(5000, TimeUnit.MILLISECONDS));
            });
    WebClient client = WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(HttpClient.from(tcpClient)))
            .build();

    Mono<ClientResponse> clientResponse = client
        .post()
        .uri(targetUrl)
        .contentType(MediaType.APPLICATION_JSON)
        .header("apiKey", apiKey)
        .body(Mono.just(siteKeyRequest), SiteKeyRequest.class)
        .exchange()
        .timeout(Duration.ofSeconds(10))
        // เพิ่ม retry 2 ครั้ง กับ exponential backoff
        .retry(2)
        // หรือใช้ retryWhen สำหรับ backoff ที่ละเอียดกว่า
        .retryWhen(reactor.util.retry.Retry.backoff(2, Duration.ofSeconds(1))
            .maxBackoff(Duration.ofSeconds(5))
            .filter(throwable ->
                throwable instanceof java.net.ConnectException ||
                throwable instanceof java.util.concurrent.TimeoutException
            )
        )
        .doOnSuccess(response -> {
            log.debug("pi response headers: " + response.headers());
            circuitBreaker.recordSuccess(siteServiceKey);
        })
        .doOnError(error -> {
            log.error("Failed to send emergency key to site: {} | Error: {}", site.getCode(), error.getMessage());
            circuitBreaker.recordFailure(siteServiceKey);
        });

    clientResponse.subscribe((response) -> {
        ClientResponse.Headers headers = response.headers();
        HttpStatus statusCode = response.statusCode();

        Mono<String> bodyToMono = response.bodyToMono(String.class);
        bodyToMono.subscribe((body) -> {
            log.info("response code from: "+ site.getCode() + " " + statusCode);

            if (statusCode.is2xxSuccessful()) {
                piSuccess.set(true);
                site.setSite_key(finalSiteKey);
                site.setEmerg_key_success(true);

                // Try sending to TTS
                try {
                    log.info("Try sending to TTS");
                    Boolean tmpStatus = submitEmergencyKeyWithCircuitBreaker(site, finalSiteKey);
                    ttsSuccess.set(tmpStatus);
                } catch (Exception e) {
                    log.error("submitEmergKeyToTTS err "+e.getMessage());
                }

                site.setKeyGenerating(false);
                site.setLastKeyUpdate(LocalDateTime.now());
                siteRepository.save(site);
                wsAnnounceSubmitKey(site, finalSiteKey, piSuccess, ttsSuccess);

            } else {
                log.debug("emergKey to site fail!! Status: " + statusCode);
            }
        }, (ex) -> {
            log.error("emergKey to site fail!! "+ex.getMessage());
            wsAnnounceSubmitKey(site, finalSiteKey, piSuccess, ttsSuccess);
        });
    }, (ex) -> {
        log.error("emergKey to site fail!! network "+ex.getMessage());
        wsAnnounceSubmitKey(site, finalSiteKey, piSuccess, ttsSuccess);
    });

    successCount.addAndGet(1);
    return site;
}).collect(Collectors.toList());
```

---

#### ปัญหา 2: submitEmergencyKey() ส่งไปยัง TTS ไม่มี Circuit Breaker (บรรทัด 291-359)

**เดิม**:
```java
private Boolean submitEmergencyKey(Site site, String emerg_key) throws IOException, JSONException {
    // ... prepare data ...

    try {
        HttpResponse<String> response = Unirest.post(targetUrl)
                .header("Content-Type", "application/json")
                .header("apiKey", apikey_by_ttsm)
                .body(outputArray.toString())
                .asString();  // ไม่มี timeout, ไม่มี retry, ไม่มี circuit breaker!

        // ... handle response ...
    } catch (UnirestException e) {
        log.error("submitEmergKeyToTTS err "+e.getMessage());
    }
    return result;
}
```

**ปัญหา**:
- ไม่มี timeout control (Unirest default timeout อาจนาน)
- ไม่มี retry
- ไม่มี circuit breaker
- ถ้า TTS service ล่ม จะทำให้ emergency key generation ช้าลง

**แก้ไข**: สร้าง wrapper method ที่มี circuit breaker

```java
@Autowired
private SimpleCircuitBreakerService circuitBreaker;

private static final String TTS_SERVICE_KEY = "tts-emergency-key";

// Method ใหม่ที่มี circuit breaker
private Boolean submitEmergencyKeyWithCircuitBreaker(Site site, String emerg_key) {
    // เช็ค circuit breaker ก่อน
    if (circuitBreaker.isOpen(TTS_SERVICE_KEY)) {
        log.warn("TTS circuit is OPEN, skipping emergency key submission for site: {}", site.getCode());
        return false;
    }

    try {
        Boolean result = submitEmergencyKey(site, emerg_key);
        if (result) {
            circuitBreaker.recordSuccess(TTS_SERVICE_KEY);
        } else {
            circuitBreaker.recordFailure(TTS_SERVICE_KEY);
        }
        return result;
    } catch (Exception e) {
        log.error("Failed to submit emergency key to TTS for site: {} | Error: {}",
                  site.getCode(), e.getMessage());
        circuitBreaker.recordFailure(TTS_SERVICE_KEY);
        return false;
    }
}

// แก้ไข submitEmergencyKey() เดิมให้มี timeout
private Boolean submitEmergencyKey(Site site, String emerg_key) throws IOException, JSONException {
    Boolean result = false;
    JSONArray cabinetList = new JSONArray();
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

    JSONArray outputArray = new JSONArray();
    outputArray.put(siteJson);

    String ttsm_ip = env.getProperty("ttsm_ip");
    String ttsm_port = env.getProperty("ttsm_port");
    String endpoint = env.getProperty("ttsm_endpoint_update_emerg_key");
    String apikey_by_ttsm = env.getProperty("apikey_by_ttsm");
    String targetUrl = "" + ttsm_ip + ":" + ttsm_port + "/" + endpoint;

    try {
        log.info(" " + site.getCode() + " send emerg to TTS "+targetUrl);

        // เพิ่ม timeout control (connection: 3s, socket: 5s)
        Unirest.setTimeouts(3000, 5000);

        HttpResponse<String> response = Unirest.post(targetUrl)
                .header("Content-Type", "application/json")
                .header("apiKey", apikey_by_ttsm)
                .body(outputArray.toString())
                .asString();

        String responseString = response.getBody();
        log.info(" TTS responseString: "+responseString);

        JSONObject jsonResp = new JSONObject(responseString);
        String resultCode = String.valueOf( jsonResp.get("resultCode") );

        if (resultCode.equals("0")) {
            result = true;
            log.info("submitEmergKeyToTTS success for site: {}", site.getCode());
        } else {
            log.warn("submitEmergKeyToTTS failed for site: {} | resultCode: {}",
                     site.getCode(), resultCode);
        }

    } catch (UnirestException e) {
        String errMsg = "Failed submitEmergKeyToTTS: " + e.getMessage();
        log.error("submitEmergKeyToTTS err for site: {} | {}", site.getCode(), errMsg);
        throw new IOException(errMsg, e);
    } finally {
        // Reset timeout เป็นค่าปกติ
        Unirest.setTimeouts(5000, 10000);
    }

    return result;
}
```

**ประโยชน์**:
- ป้องกัน TTS service down ทำให้ emergency key generation ช้า
- Auto-skip TTS submission เมื่อ service ล่ม
- Timeout control ป้องกัน hang
- Better error logging

---

### 2. ปรับใช้ RetryableHttpService ใน SSWSService.java

**ที่ตำแหน่ง**: `src/main/java/dev/niceone/smartsiteappserver/service/SSWSService.java`

**เดิม**:
```java
ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
```

**ใหม่**:
```java
@Autowired
private RetryableHttpService httpService;

String serviceKey = "ssws-api-" + siteCode;
ResponseEntity<String> response = httpService.getWithRetry(
    serviceKey, restTemplate, url, String.class
);
```

**ประโยชน์**: Auto retry + circuit breaker สำหรับ site communication

---

### 2. ปรับใช้ Circuit Breaker สำหรับ External Services

**ใน AlarmLogCasService.java** (เรียก CAS Alarm Service):

```java
@Autowired
private SimpleCircuitBreakerService circuitBreaker;

private static final String CAS_SERVICE_KEY = "cas-alarm-service";

public void sendToCAS(AlarmLog log) {
    if (circuitBreaker.isOpen(CAS_SERVICE_KEY)) {
        log.warn("CAS service circuit is OPEN, skipping alarm send");
        return; // ไม่ส่ง
    }

    try {
        // เรียก CAS service
        casClient.sendAlarm(log);
        circuitBreaker.recordSuccess(CAS_SERVICE_KEY);
    } catch (Exception e) {
        circuitBreaker.recordFailure(CAS_SERVICE_KEY);
        log.error("Failed to send to CAS", e);
    }
}
```

---

### 3. Rebalance Server Load

**ปัญหา**: a2ssm01 รับ load 36.2% (มากกว่าคนอื่น 5 เท่า)

**วิธีแก้** (ต้องทำ manual):

```sql
-- 1. เช็ค site distribution
SELECT
    server_name,
    COUNT(*) as site_count,
    ROUND(COUNT(*) * 100.0 / SUM(COUNT(*)) OVER(), 2) as percentage
FROM sites
GROUP BY server_name
ORDER BY site_count DESC;

-- 2. ถ้าพบว่าไม่สมดุล migrate บาง sites
UPDATE sites
SET server_name = 'a1ssm01'  -- server ที่ load น้อย
WHERE server_name = 'a2ssm01'  -- server ที่ load เยอะ
AND site_code IN (
    SELECT site_code
    FROM sites
    WHERE server_name = 'a2ssm01'
    ORDER BY site_code
    LIMIT 1000  -- migrate ทีละ 1000 sites
);
```

**หรือใช้ Load Balancer** (ถ้ามี):
- Round-robin DNS
- Nginx upstream balancing
- HAProxy

---

### 4. ปรับ Slow Query (ถ้าพบ)

**เปิด slow query log ชั่วคราว**:
```properties
# ใน application.properties
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE

# ดู query ที่ช้า
spring.jpa.properties.hibernate.generate_statistics=true
```

**วิเคราะห์ log**:
```bash
grep "org.hibernate.SQL" application.log | grep -v "SELECT" > slow_queries.log
```

---

## 📊 Metrics ที่ควร Monitor

### 1. Connection Pool
- Active connections (ควร < 80% ของ max pool)
- Idle connections (ควร > 20%)
- Threads waiting (ควรเป็น 0)

### 2. Circuit Breaker
- จำนวน circuits ที่ OPEN
- Service recovery time
- Failure rate per service

### 3. Application
- Response time (ควร < 2 วินาที)
- Error rate (ควร < 1%)
- Communication Lost events (ควร < 10/hour)

---

## ⚠️ Alert Thresholds (ตั้งใน monitoring system)

```yaml
alerts:
  connection_pool:
    - utilization > 80% for 3 minutes → WARNING
    - utilization > 90% for 1 minute → CRITICAL
    - threads_waiting > 0 → WARNING

  circuit_breaker:
    - circuit OPEN > 5 minutes → WARNING
    - > 3 circuits OPEN simultaneously → CRITICAL

  communication_lost:
    - > 100 sites/minute → WARNING
    - > 500 sites/minute → CRITICAL

  error_rate:
    - > 100 errors/minute → WARNING
    - > 500 errors/minute → CRITICAL
```

---

## 🎓 บทเรียนที่ได้จาก Log Analysis

### 1. Database Connection Pool ต้องมี Headroom
- **ผิด**: Pool size 30 สำหรับ 8 servers + load สูง
- **ถูก**: Pool size ควรเป็น 3-4 เท่าของ CPU cores
- **ปัจจุบัน**: ปรับเป็น 100 (3.3 เท่า)

### 2. External Service Down → Cascade Failures
- **ผิด**: ไม่มี circuit breaker → retry ไม่รู้จบ → thread blocked
- **ถูก**: Circuit breaker + timeout + fallback

### 3. Monitoring คือสิ่งจำเป็น
- **ผิด**: รู้ว่ามีปัญหาตอนระบบพังแล้ว
- **ถูก**: Monitor pool usage → เตือนก่อนพัง

### 4. Retry ต้องมี Backoff
- **ผิด**: Retry ทันที → thundering herd
- **ถูก**: Exponential backoff + jitter

---

## 📞 หากพบปัญหา

### Common Issues

#### 1. Pool Timeout ยังเกิดอยู่
```
✓ เช็ค: Pool size เพิ่มหรือยัง (ควรเป็น 100)
✓ ดู: Slow queries (query > 5 วินาที)
✓ เช็ค: Connection leak (connection ไม่ถูก close)
```

#### 2. Circuit Breaker ไม่ทำงาน
```
✓ เช็ค: @Service annotation มีครบหรือไม่
✓ ดู: Log มี "Circuit" หรือไม่
✓ ทดสอบ: Shutdown external service จงใจ แล้วดู log
```

#### 3. Communication Lost ยังเยอะอยู่
```
✓ เช็ค: Network infrastructure (router, switch)
✓ ดู: Site PMTMS (มีปัญหามากที่สุด 28 ครั้ง)
✓ เช็ค: Server a2ssm01 load balance
```

---

## ✅ Checklist Before Production Deploy

- [ ] Compile สำเร็จ (mvnw clean package)
- [ ] ทดสอบใน dev environment ก่อน
- [ ] Backup database
- [ ] Backup old WAR file
- [ ] ทดสอบ rollback plan
- [ ] แจ้ง users ถ้ามี downtime
- [ ] Monitor logs หลัง deploy อย่างน้อย 30 นาที
- [ ] เช็ค pool monitoring ทำงาน
- [ ] เช็ค circuit breaker ทำงาน

---

## 📝 สรุปปัญหาที่พบใน generateEmergencyKey()

### Impact ถ้าไม่แก้

1. **Site Emergency Key Generation Failures** (ระดับ: HIGH)
   - Network hiccup ทำให้ key generation fail ทันที
   - ไม่มี retry = site ต้องรอรอบถัดไป (2 นาที)
   - Site ที่ offline ชั่วคราวจะไม่ได้รับ emergency key

2. **TTS Service Down → Cascade Slowdown** (ระดับ: CRITICAL)
   - TTS service ล่ม → ทุก site ที่ส่ง key ต้องรอ Unirest timeout
   - Default Unirest timeout = 10+ วินาที × จำนวน sites = ช้ามาก
   - Scheduler runs ทุก 2 นาที → blocked threads pile up

3. **Circuit Breaker ขาดหาย**
   - ไม่มีการ track ว่า site หรือ TTS ไหนมีปัญหา
   - Retry ซ้ำ ๆ กับ endpoint ที่รู้ว่าล่มอยู่แล้ว
   - Waste resources และเวลา

### ประโยชน์ถ้าแก้

1. **Reliability**: Site ที่ offline ชั่วคราวได้รับ key ด้วย retry
2. **Performance**: TTS down ไม่ทำให้ scheduler ช้า (circuit breaker skip)
3. **Observability**: Log ชัดเจนว่า failure เกิดจาก site หรือ TTS
4. **Graceful Degradation**: System ยังทำงานต่อได้แม้บางส่วนล่ม

### Priority

- **แก้ TTS Circuit Breaker ก่อน** (เพราะ TTS down impact ทุก sites)
- **แก้ Site Retry ทีหลัง** (impact แค่ site ที่มีปัญหาเท่านั้น)

---

**สร้างโดย**: Claude Code Analysis
**วันที่**: 2025-12-18
**สถานะ**: Ready for Offline Deployment ✅
**การอัพเดทล่าสุด**: เพิ่มการวิเคราะห์ปัญหา generateEmergencyKey()
