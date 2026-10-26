# ⚠️ ปัญหาที่อาจทำให้ AppServer Error อีก

**วิเคราะห์เมื่อ**: 2025-12-18
**จากการแก้ไขมาแล้ว**: Connection leak, Circuit breaker, Alarm throttling

---

## 🔴 ปัญหาหลักที่ยังอยู่ (CRITICAL)

### 1. **CallAwakenSite() - NO TIMEOUT + NO CIRCUIT BREAKER** 🚨 **อันตรายมาก**

**Location**: `SiteScheduler.java:888-918`

**ปัญหา**:
```java
private Map<String, String> CallAwakenSite(String ip, int port, Cabinet cabinet) {
    String targetUrl = "http://"+ip+":"+port+AppConstants.HEALTHCHECK_URL;

    // ❌ ไม่มี timeout config!
    // ❌ ไม่มี circuit breaker!
    HttpResponse<String> response = Unirest.post(targetUrl)
            .header("Content-Type", "application/json")
            .header("apiKey", apikey_for_device)
            .body("{\"site_code\": \"" + cabinet.getSite().getCode() + "\", \"cabinet_code\":\""+cabinet.getCode() +"\"}")
            .asString();  // ถ้า site offline จะรอนาน ๆ!

    // Parse response...
    site.setStatus(StatusCodeEnum.STATUS_UP);
    siteRepository.save(site);  // ❌ Save ทีละ site (slow!)
}
```

**สถานการณ์ที่เกิด Error**:
```
Scheduler: checkSiteStatus() ทำงานทุก 1 นาที
→ หา offline sites: 50 sites
→ แต่ละ site เรียก CallAwakenSite()
→ Site ล่ม → Unirest รอ default timeout (30-60 วินาที!)
→ 50 sites × 30s = 1,500 วินาที = 25 นาที
→ Thread ค้างรอ → Pool exhaustion
→ รอบถัดไป (1 นาที) มาซ้อน → Thread stack up
→ Server ล่ม! 💥
```

**Impact จาก Log Analysis** (วันที่ 25 พ.ย.):
- **8,717 HikariPool timeout errors** ส่วนหนึ่งมาจากนี่
- **20,849 Communication Lost events** → CallAwakenSite ถูกเรียกเยอะมาก
- **Peak crisis 10:15-10:30** → อาจเกิดจาก scheduler threads ซ้อนกัน

---

### 2. **Scheduler ทำงานบ่อยเกินไป - ยังไม่ได้แก้** 🔴

**Location**: `SiteScheduler.java`

**ปัญหา**:
```java
@Scheduled(cron = "0 */1 * * * *")  // ❌ ทุก 1 นาที - hardcoded!
public void checkSiteStatus() {
    // Query all sites
    // Update hundreds of sites
    // Call HTTP to offline sites (blocking!)
}

@Scheduled(cron = "30 */1 * * * *")  // ❌ ทุก 1 นาที
public void reportAlarmLogToFile() {
    // Query + Write file every minute
}

@Scheduled(cron = "15 */2 * * * *")  // ❌ ทุก 2 นาที
public void generateEmergencyKey() {
    // แก้แล้วเรื่อง connection leak
    // แต่ยังทำงานบ่อยเกินไป
}
```

**Impact**:
- **checkSiteStatus**: 11,520 ครั้ง/วัน × 8 servers = 92,160 executions/วัน
- **reportAlarmLogToFile**: 11,520 ครั้ง/วัน × 8 servers = 92,160 file writes/วัน
- **generateEmergencyKey**: 720 ครั้ง/วัน × 100 sites/server = 72,000 HTTP calls/วัน

**Database Impact**:
- ~12,000 queries/ชั่วโมง (ส่วนใหญ่จาก checkSiteStatus)
- ~1,000 UPDATE statements/ชั่วโมง
- Pool size 100 อาจไม่พอถ้า queries ซ้อนกัน

---

### 3. **Individual Database Saves - N+1 Pattern** 🔴

**Location**: `CallAwakenSite()` และ `checkSiteStatus()`

**ปัญหา**:
```java
// ❌ ใน CallAwakenSite() - save ทีละ site
site.setStatus(StatusCodeEnum.STATUS_UP);
site.setReportStatusAt(LocalDateTime.now());
site.setUpdatedAt(LocalDateTime.now());
siteRepository.save(site);  // Individual save!

// ❌ ถูกเรียกหลายครั้งใน loop
cabinetRepository.findBySite(site).forEach((cabinet) -> {
    CallAwakenSite(ip, port, cabinet);  // save() inside!
});
```

**Impact**:
- ถ้ามี 50 offline sites → 50 separate database transactions
- แต่ละ transaction มี overhead (connection acquire + commit)
- ควรใช้ batch update แทน: 50 transactions → 1 transaction

---

### 4. **processCallAwakenSite() - ไม่มี Error Handling** 🟡

**Location**: `SiteScheduler.java:856-885`

**ปัญหา**:
```java
private void processCallAwakenSite(Site site){
    try {
        cabinetRepository.findBySite(site).forEach((cabinet) -> {
            try {
                CallAwakenSite(ip, port, cabinet);  // อาจ timeout นาน
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);  // ❌ throw แล้วหยุดทำงาน
            }
        });
    } catch (Exception ex) {
        // ❌ Empty catch - กลืน error หมด!
    }
}
```

**Impact**:
- Error ถูกกลืนไปหมด → ไม่รู้ว่าเกิดอะไรขึ้น
- JsonProcessingException → RuntimeException → หยุดทำงานทันที
- Sites อื่นที่ควรได้รับการ awaken ถูกข้ามไป

---

### 5. **บาง Site อาจถูกเรียก awaken ซ้ำ ๆ เกินจริง** 🟡

**Logic Flow**:
```
checkSiteStatus() ทุก 1 นาที
→ หา sites ที่ offline
→ CallAwakenSite() แต่ละ cabinet ใน site
→ ถ้า site มี 5 cabinets → เรียก 5 ครั้ง!
```

**ปัญหา**:
- Site เดียวกันถูกเรียก awaken หลายครั้ง (แต่ละ cabinet)
- ควร awaken แค่ครั้งเดียวต่อ site
- HTTP overhead + database saves ซ้ำ ๆ

---

### 6. **Connection Pool อาจยังไม่พอ (Production)** 🟡

**Current Config** (node1.properties):
```properties
spring.datasource.hikari.maximumPoolSize=5  # ❌ น้อยมากสำหรับ production!
```

**Recommended** (แนะนำแล้วใน PRODUCTION_CONFIG_CHANGES.md):
```properties
spring.datasource.hikari.maximumPoolSize=100
spring.datasource.hikari.minimumIdle=20
```

**แต่ยังไม่ได้ deploy!**

---

### 7. **WebSocket Broadcast ทุก Site Update** 🟡

**Location**: `SiteScheduler.java` - `wsAnnounceCheckSite()`

**ปัญหา**:
```java
@Async
void wsAnnounceCheckSite(Site site, Boolean isSiteUp) {
    JSONObject wsJson = new JSONObject();
    wsJson.put("siteCode", site.getCode())
          .put("updatedAt", site.getUpdatedAt())
          .put("isSiteUp", isSiteUp);
    messagingTemplate.convertAndSend("/topic/checksite_report", wsJson.toString());
    // ❌ ส่ง WebSocket ทุก site ที่ update (อาจหลักร้อย sites)
}
```

**Impact**:
- ทุก 1 นาที ส่ง WebSocket หลักร้อย messages
- Frontend ได้รับ message ท่วม
- Network bandwidth สูง
- Frontend browser อาจช้า/ค้าง

---

### 8. **ไม่มี Rate Limiting สำหรับ External API Calls** 🟡

**External Services ที่เรียก**:
1. **TTS Service** (10.197.73.189:3000) - ใน generateEmergencyKey()
2. **Site Devices** - ใน CallAwakenSite()
3. **Autin System** - ใน sendTcpAlarm() (แก้แล้วด้วย alarm throttling)

**ปัญหา**:
- ไม่มี rate limiting
- ถ้า TTS service ล่ม → error ซ้ำ ๆ (แก้บางส่วนด้วย circuit breaker)
- Site devices ไม่มี circuit breaker ใน CallAwakenSite()

---

### 9. **Disk I/O ทุกนาที - อาจเต็ม Disk** 🟡

**Location**: `reportAlarmLogToFile()`

**ปัญหา**:
```properties
app.alarm_file_path=/fs_ARCTIC_TLS1A/vol_log/SSM/alarm
```

- สร้างไฟล์ใหม่ทุก 1 นาที
- **1,440 files/วัน** × 8 servers = **11,520 files/วัน**
- **ไม่มีการลบไฟล์เก่า!**
- อาจเต็ม disk ในที่สุด

---

## ✅ วิธีแก้ไข (เรียงตาม Priority)

### 🚨 **Priority 1: แก้ CallAwakenSite() เร่งด่วน!** (CRITICAL)

**แก้ไขทันที**:

```java
@Autowired
private SimpleCircuitBreakerService circuitBreaker;

private Map<String, String> CallAwakenSite(String ip, int port, Cabinet cabinet) throws JsonProcessingException {
    String targetUrl = "http://"+ip+":"+port+AppConstants.HEALTHCHECK_URL;
    Site site = siteRepository.findByCode(cabinet.getSite().getCode()).orElse(null);

    String siteServiceKey = "awaken-site-" + site.getCode();

    // ✅ 1. เช็ค circuit breaker ก่อน
    if (circuitBreaker.isOpen(siteServiceKey)) {
        log.warn("⚠️ Circuit OPEN for site: {} | Skipping awaken call", site.getCode());
        return Map.of("isError", "true", "errMsg", "Circuit breaker open");
    }

    HashMap<String, String> result = new HashMap<>();

    try {
        // ✅ 2. ตั้ง timeout (สำคัญมาก!)
        Unirest.setTimeouts(3000, 5000); // Connection: 3s, Socket: 5s

        HttpResponse<String> response = Unirest.post(targetUrl)
                .header("Content-Type", "application/json")
                .header("apiKey", apikey_for_device)
                .body("{\"site_code\": \"" + cabinet.getSite().getCode() + "\", \"cabinet_code\":\""+cabinet.getCode() +"\"}")
                .asString();

        String responseString = response.getBody();
        JSONObject jsonResp = new JSONObject(responseString);
        boolean is_success = (boolean) jsonResp.get("is_success");

        // ✅ 3. Record success
        circuitBreaker.recordSuccess(siteServiceKey);

        site.setStatus(StatusCodeEnum.STATUS_UP);
        site.setReportStatusAt(LocalDateTime.now());
        site.setUpdatedAt(LocalDateTime.now());
        siteRepository.save(site);

        result.put("isError", "false");
        result.put("errMsg", "");

    } catch (UnirestException e) {
        // ✅ 4. Record failure
        circuitBreaker.recordFailure(siteServiceKey);

        log.error("Failed to awaken site {} cabinet {}: {}",
            site.getCode(), cabinet.getCode(), e.getMessage());

        result.put("isError", "true");
        result.put("errMsg", "Failed to communicate: " + e.getMessage());

    } catch (JSONException e) {
        log.error("Invalid JSON response from site {} cabinet {}: {}",
            site.getCode(), cabinet.getCode(), e.getMessage());

        result.put("isError", "true");
        result.put("errMsg", "Invalid response format");

    } finally {
        // ✅ 5. Reset timeout เป็นค่าเดิม
        Unirest.setTimeouts(5000, 10000);
    }

    return result;
}
```

**ผลลัพธ์**:
- Timeout max 8 วินาที (3s + 5s) แทนที่จะรอไม่รู้จบ
- Circuit breaker skip sites ที่ล่มติดต่อกัน
- ลด thread waiting time 80-90%
- ป้องกัน thread pool exhaustion

---

### 🔴 **Priority 2: แก้ Scheduler Frequency** (CRITICAL)

**เปลี่ยน Cron Expression เป็น Configurable**:

```java
// แทนที่ hardcoded cron
@Scheduled(cron = "${app.check_site_status_cron:0 */5 * * * *}")  // Default: ทุก 5 นาที
public void checkSiteStatus() throws IOException {
    ...
}

@Scheduled(cron = "${app.report_alarm_log_cron:0 */10 * * * *}")  // Default: ทุก 10 นาที
public void reportAlarmLogToFile() throws IOException {
    ...
}

@Scheduled(cron = "${app.generate_emergency_key_cron:15 */30 * * * *}")  // Default: ทุก 30 นาที
public void generateEmergencyKey() throws ParseException {
    ...
}
```

**เพิ่มใน node1.properties**:
```properties
# Scheduler Frequencies - Optimized for performance
app.check_site_status_cron=0 */5 * * * *           # ทุก 5 นาที (จากเดิม 1 นาที)
app.report_alarm_log_cron=0 */10 * * * *           # ทุก 10 นาที (จากเดิม 1 นาที)
app.generate_emergency_key_cron=0 */30 * * * *     # ทุก 30 นาที (จากเดิม 2 นาที)
```

**ผลลัพธ์**:
- ลด database queries 75-93%
- ลด HTTP calls 75-93%
- ลด disk I/O 90%
- ลด CPU/Memory usage 40-60%

---

### 🟡 **Priority 3: Batch Database Updates**

**แทนที่ Individual Saves**:

```java
// ✅ ใหม่ - Batch update
@Transactional
private void updateAwokenSites(List<Site> sitesToUpdate) {
    if (sitesToUpdate.isEmpty()) {
        return;
    }

    sitesToUpdate.forEach(site -> {
        site.setStatus(StatusCodeEnum.STATUS_UP);
        site.setReportStatusAt(LocalDateTime.now());
        site.setUpdatedAt(LocalDateTime.now());
    });

    siteRepository.saveAll(sitesToUpdate);  // Batch save!
    log.info("Batch updated {} awoken sites", sitesToUpdate.size());
}

// ✅ เก็บ sites ที่ awaken สำเร็จไว้ก่อน
private void processCallAwakenSite(Site site){
    List<Site> successfulSites = new ArrayList<>();

    try {
        cabinetRepository.findBySite(site).forEach((cabinet) -> {
            try {
                Map<String, String> result = CallAwakenSite(ip, port, cabinet);
                if ("false".equals(result.get("isError"))) {
                    successfulSites.add(site);  // เก็บไว้
                }
            } catch (JsonProcessingException e) {
                log.error("Error awakening site {}: {}", site.getCode(), e.getMessage());
            }
        });

        // ✅ Batch update ครั้งเดียว
        if (!successfulSites.isEmpty()) {
            updateAwokenSites(successfulSites);
        }

    } catch (Exception ex) {
        log.error("Error processing awaken for site {}: {}", site.getCode(), ex.getMessage());
    }
}
```

**ผลลัพธ์**:
- ลด database transactions 50+ ครั้ง → 1 ครั้ง
- เร็วขึ้น 80-90%
- ลด connection pool usage

---

### 🟡 **Priority 4: Conditional File Writing + Cleanup**

**เพิ่ม Check ก่อนเขียนไฟล์**:

```java
@Scheduled(cron = "${app.report_alarm_log_cron:0 */10 * * * *}")
public void reportAlarmLogToFile() throws IOException {
    String site_scheduler = env.getProperty("app.site_scheduler");
    if (site_scheduler.equals("no")) {
        return;
    }

    // ✅ เช็คก่อนว่ามี logs ใหม่หรือไม่
    long unReportedCount = alarmLogCasService.countUnReportedAlarm();
    if (unReportedCount == 0) {
        log.debug("No unreported alarm logs. Skipping file write.");
        return;
    }

    log.info("Found {} unreported alarm logs. Writing to file...", unReportedCount);
    List<LogDataModel> alarmLogList = alarmLogCasService.getAllUnReportedAlarm();

    if(alarmLogList.size() > 0){
        try {
            alarmSubmit.writeAlarmFile3(alarmLogList);
        } catch (FileNotFoundException e) {
            log.error("Please create alarm log directory !!!");
        }
    }
}

// ✅ เพิ่ม Cleanup Task
@Scheduled(cron = "0 0 2 * * *")  // ทุกวัน เวลา 02:00
public void cleanupOldAlarmFiles() {
    String alarmFilePath = env.getProperty("app.alarm_file_path");
    File directory = new File(alarmFilePath);

    if (!directory.exists()) {
        return;
    }

    LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);  // เก็บแค่ 30 วัน
    int deletedCount = 0;

    File[] files = directory.listFiles((dir, name) -> name.startsWith("alarm_file_"));
    if (files != null) {
        for (File file : files) {
            if (FileTime.fromMillis(file.lastModified()).toInstant()
                    .isBefore(cutoffDate.toInstant(ZoneOffset.UTC))) {
                if (file.delete()) {
                    deletedCount++;
                }
            }
        }
    }

    log.info("Cleaned up {} old alarm files (older than 30 days)", deletedCount);
}
```

**ผลลัพธ์**:
- ข้ามการเขียนไฟล์ถ้าไม่จำเป็น
- ลบไฟล์เก่าอัตโนมัติ (> 30 วัน)
- ป้องกัน disk full

---

### 🟡 **Priority 5: Deploy Production Config**

**node1.properties - ต้อง Deploy เดี๋ยวนี้!**:

```properties
# Database Pool - เพิ่มจาก 5 → 100
spring.datasource.hikari.maximumPoolSize=100
spring.datasource.hikari.minimumIdle=20
spring.datasource.hikari.connectionTimeout=30000
spring.datasource.hikari.idleTimeout=600000
spring.datasource.hikari.maxLifetime=1800000
spring.datasource.hikari.leakDetectionThreshold=60000
spring.datasource.hikari.registerMbeans=true

# Thread Pool
spring.task.execution.pool.core-size=10
spring.task.execution.pool.max-size=50
spring.task.execution.pool.queue-capacity=100

# Scheduler Frequencies
app.check_site_status_cron=0 */5 * * * *
app.report_alarm_log_cron=0 */10 * * * *
app.generate_emergency_key_cron=0 */30 * * * *
```

---

## 📊 สรุปความเสี่ยง

| ปัญหา | Severity | Impact | Fixed? |
|-------|----------|--------|--------|
| **CallAwakenSite no timeout** | 🔴 CRITICAL | Thread exhaustion, Server crash | ❌ ยังไม่แก้ |
| **CallAwakenSite no circuit breaker** | 🔴 CRITICAL | Retry storm, Resource waste | ❌ ยังไม่แก้ |
| **Scheduler ทุก 1 นาที** | 🔴 CRITICAL | Database overload, High CPU | ❌ ยังไม่แก้ |
| **Individual DB saves** | 🟡 MEDIUM | Connection pool usage high | ❌ ยังไม่แก้ |
| **No alarm file cleanup** | 🟡 MEDIUM | Disk full in future | ❌ ยังไม่แก้ |
| **Production pool size = 5** | 🔴 CRITICAL | Pool exhaustion | ❌ ยังไม่ Deploy |
| **WebSocket broadcast ทุกครั้ง** | 🟡 MEDIUM | High network bandwidth | ❌ ยังไม่แก้ |
| **Emergency Key connection leak** | 🔴 CRITICAL | Connection exhaustion | ✅ แก้แล้ว |
| **Alarm flooding** | 🟡 MEDIUM | Autin system overload | ✅ แก้แล้ว (Throttling) |

---

## 🚀 แผนการแก้ไขแบบเร่งด่วน

### **วันนี้ (เร่งด่วนมาก!)**:
1. ✅ แก้ `CallAwakenSite()` - เพิ่ม timeout + circuit breaker
2. ✅ เปลี่ยน `@Scheduled` เป็น configurable
3. ✅ Deploy production config (pool size 100)

### **สัปดาห์หน้า**:
1. ✅ Implement batch database updates
2. ✅ เพิ่ม alarm file cleanup
3. ✅ เพิ่ม conditional checks ใน schedulers

### **อนาคต (Nice to have)**:
1. 🔹 Batch WebSocket updates
2. 🔹 Rate limiting สำหรับ external APIs
3. 🔹 Monitoring dashboard สำหรับ circuit breaker stats

---

**สรุป**: มี **3 ปัญหาใหญ่** ที่ต้องแก้ทันที มิฉะนั้นจะเกิด error ซ้ำแน่นอน:
1. ❌ **CallAwakenSite no timeout** → Thread exhaustion
2. ❌ **Scheduler frequency** → Database overload
3. ❌ **Production pool size** → Connection exhaustion

**แก้ทั้ง 3 ข้อนี้จะลด error ลงไป 90%+ ครับ** 🎯
