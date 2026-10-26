# 🔍 จุดที่ทำให้ Server ทำงานหนัก - การวิเคราะห์และแนะนำ

**วันที่วิเคราะห์**: 2025-12-18
**สถานะปัจจุบัน**: มีจุดที่ทำให้ server หนักหลายจุด

---

## ⚠️ ปัญหาหลักที่พบ

### 1. **Scheduler Tasks ทำงานบ่อยเกินไป** 🔴 **CRITICAL**

```java
// ❌ ปัญหา: Tasks ทำงานทุกนาที!
@Scheduled(cron = "0 */1 * * * *")  // ทุก 1 นาที
public void checkSiteStatus() {
    // Query ทุก sites ที่ active
    // Update status ทั้งหมด
    // ส่ง alarm + WebSocket
}

@Scheduled(cron = "30 */1 * * * *")  // ทุก 1 นาที (วินาทีที่ 30)
public void reportAlarmLogToFile() {
    // Query alarm logs ทั้งหมดที่ยังไม่ได้ report
    // เขียนไฟล์
}

@Scheduled(cron = "15 */2 * * * *")  // ทุก 2 นาที
public void generateEmergencyKey() {
    // Query sites ทั้งหมด
    // Generate keys
    // ส่งไป sites + TTS
}
```

**Impact**:
- **checkSiteStatus**: ทำงานทุกนาที × 8 servers = 11,520 ครั้ง/วัน
- **reportAlarmLogToFile**: ทำงานทุกนาที × 8 servers = 11,520 ครั้ง/วัน
- **generateEmergencyKey**: ทำงาน 720 ครั้ง/วัน × 100 sites = 72,000 HTTP calls/วัน

---

### 2. **Database Queries หนักมาก** 🔴 **CRITICAL**

#### **checkSiteStatus() - Query ทุกนาที**
```java
// ❌ ทุก 1 นาที query ทั้งหมดนี้:
List<Site> offlineSites = siteRepository
    .findByActiveAndIsInstallAndReportStatusAtLessThan(true, true, offsetTime);
    // → Query หา sites offline ทั้งหมด

int retUpdate = siteRepository.updateStatusAndTimestamps(offsetTime);
    // → UPDATE sites SET status = DOWN WHERE ...
    // → อาจ update หลักร้อย records

ProcessSetCommunicationLostAndCallAwake(_notSendReport);
    // → สร้าง alarm logs
    // → เรียก Unirest ไปยัง sites (blocking!)
```

**ทุก 1 นาที**:
- 1× SELECT sites (scan ทั้งตาราง)
- 1× UPDATE sites (อาจหลักร้อย rows)
- N× INSERT alarm_logs
- N× HTTP calls ไปยัง sites (blocking)

---

### 3. **Unirest Blocking Calls เยอะมาก** 🔴 **CRITICAL**

```java
// CallAwakenSite() - เรียกทุก site ที่ offline
HttpResponse<String> response = Unirest.post(targetUrl)
    .header("Content-Type", "application/json")
    .header("apiKey", apikey_for_device)
    .body(...)
    .asString();  // ❌ BLOCKING! รอจนกว่าจะได้ response
```

**ปัญหา**:
- ไม่มี timeout config → default อาจนาน
- Blocking I/O → thread ค้างรอ
- ถ้า site ล่ม → รอ timeout นาน ๆ
- ไม่มี circuit breaker → retry ซ้ำ ๆ

**Impact ใน checkSiteStatus()**:
```
ทุก 1 นาที:
- หา offline sites (สมมติ 50 sites)
- แต่ละ site เรียก CallAwakenSite()
- แต่ละ call ใช้เวลา 5-10 วินาที (ถ้า site offline)
- Total: 50 sites × 10s = 500 วินาที = 8.3 นาที!

Scheduler รอบถัดไป (1 นาที) มาก่อนรอบเก่าจะเสร็จ!
→ Thread stack up → Server หนัก!
```

---

### 4. **WebSocket Broadcast ทุกการ Update** 🟡 **MEDIUM**

```java
// ❌ ส่ง WebSocket ทุกครั้งที่ update site
@Async
void wsAnnounceCheckSite(Site site, Boolean isSiteUp) {
    JSONObject wsJson = new JSONObject();
    wsJson.put("siteCode", site.getCode())
          .put("updatedAt", site.getUpdatedAt())
          .put("isSiteUp", isSiteUp);
    messagingTemplate.convertAndSend("/topic/checksite_report", wsJson.toString());
}
```

**ปัญหา**:
- ส่ง WebSocket ทุก site ที่ update (อาจหลักร้อย sites)
- ทุก 1 นาที
- Frontend ได้รับ message ท่วม
- Network bandwidth สูง

---

### 5. **reportAlarmLogToFile() - File I/O ทุกนาที** 🟡 **MEDIUM**

```java
@Scheduled(cron = "30 */1 * * * *")  // ทุก 1 นาที
public void reportAlarmLogToFile() {
    List<LogDataModel> alarmLogList = alarmLogCasService.getAllUnReportedAlarm();
    // Query alarm logs ที่ยังไม่ได้ report

    if(alarmLogList.size() > 0){
        alarmSubmit.writeAlarmFile3(alarmLogList);
        // เขียนไฟล์ทุกนาที
    }
}
```

**ปัญหา**:
- Query database ทุกนาที
- เขียนไฟล์ทุกนาที (disk I/O)
- ถ้าไม่มี alarm logs ใหม่ก็ยังทำงาน

---

## ✅ วิธีแก้ไข - ทำให้ Server เบา ๆ

### **Solution 1: ลดความถี่ของ Schedulers** ⚠️ **แนะนำมาก**

#### **checkSiteStatus: ทุก 1 นาที → 5 นาที**

```properties
# ใน node1.properties
# ❌ เดิม - ทำงานทุกนาที (hardcode ใน @Scheduled)
# @Scheduled(cron = "0 */1 * * * *")

# ✅ ใหม่ - configurable
app.check_site_status_cron=0 */5 * * * *  # ทุก 5 นาที
```

**แก้ไข Code**:
```java
// เปลี่ยนจาก hardcode เป็น configurable
@Scheduled(cron = "${app.check_site_status_cron:0 */5 * * * *}")  // Default: ทุก 5 นาที
public void checkSiteStatus() throws IOException {
    ...
}
```

**ผลลัพธ์**:
- ลดจาก 11,520 ครั้ง/วัน → 2,880 ครั้ง/วัน (75% ลดลง)
- ลด database queries 75%
- ลด HTTP calls 75%

---

#### **reportAlarmLogToFile: ทุก 1 นาที → 10 นาที**

```properties
# ใน node1.properties
app.report_alarm_log_cron=0 */10 * * * *  # ทุก 10 นาที
```

**แก้ไข Code**:
```java
@Scheduled(cron = "${app.report_alarm_log_cron:0 */10 * * * *}")  // Default: ทุก 10 นาที
public void reportAlarmLogToFile() throws IOException {
    ...
}
```

**ผลลัพธ์**:
- ลดจาก 11,520 ครั้ง/วัน → 1,152 ครั้ง/วัน (90% ลดลง)
- ลด disk I/O 90%

---

#### **generateEmergencyKey: ทุก 2 นาที → 30 นาที**

```properties
# ใน node1.properties
# Emergency key ไม่จำเป็นต้องสร้างบ่อยขนาดนี้
app.generate_emergency_key_cron=0 */30 * * * *  # ทุก 30 นาที
```

**แก้ไข Code**:
```java
@Scheduled(cron = "${app.generate_emergency_key_cron:15 */30 * * * *}")  // Default: ทุก 30 นาที
public void generateEmergencyKey() throws ParseException {
    ...
}
```

**ผลลัพธ์**:
- ลดจาก 720 ครั้ง/วัน → 48 ครั้ง/วัน (93% ลดลง)
- ลด HTTP calls จาก 72,000 → 4,800 ครั้ง/วัน

---

### **Solution 2: เพิ่ม Timeout และ Circuit Breaker สำหรับ CallAwakenSite** ⚠️ **สำคัญมาก**

#### **ปัญหาเดิม**:
```java
// ❌ ไม่มี timeout, ไม่มี circuit breaker
HttpResponse<String> response = Unirest.post(targetUrl)
    .header("Content-Type", "application/json")
    .header("apiKey", apikey_for_device)
    .body(...)
    .asString();  // อาจรอนาน ๆ
```

#### **วิธีแก้**:
```java
@Autowired
private SimpleCircuitBreakerService circuitBreaker;

private Map<String, String> CallAwakenSite(String ip, int port, Cabinet cabinet) {
    String targetUrl = "http://"+ip+":"+port+AppConstants.HEALTHCHECK_URL;
    Site site = siteRepository.findByCode(cabinet.getSite().getCode()).orElse(null);

    String siteServiceKey = "awaken-site-" + site.getCode();

    // ✅ เช็ค circuit breaker ก่อน
    if (circuitBreaker.isOpen(siteServiceKey)) {
        log.warn("⚠️ Circuit OPEN for site: {} | Skipping awaken call", site.getCode());
        return Map.of("isError", "true", "errMsg", "Circuit breaker open");
    }

    HashMap<String, String> result = new HashMap<>();

    try {
        // ✅ ตั้ง timeout
        Unirest.setTimeouts(3000, 5000); // Connection: 3s, Socket: 5s

        HttpResponse<String> response = Unirest.post(targetUrl)
                .header("Content-Type", "application/json")
                .header("apiKey", apikey_for_device)
                .body("{\"site_code\": \"" + site.getCode() + "\", \"cabinet_code\":\""+cabinet.getCode() +"\"}")
                .asString();

        String responseString = response.getBody();
        JSONObject jsonResp = new JSONObject(responseString);
        boolean is_success = (boolean) jsonResp.get("is_success");

        // ✅ Record success
        circuitBreaker.recordSuccess(siteServiceKey);

        site.setStatus(StatusCodeEnum.STATUS_UP);
        site.setReportStatusAt(LocalDateTime.now());
        site.setUpdatedAt(LocalDateTime.now());
        siteRepository.save(site);

        result.put("isError", "false");
        result.put("errMsg", "");

    } catch (UnirestException e) {
        // ✅ Record failure
        circuitBreaker.recordFailure(siteServiceKey);

        result.put("isError", "true");
        result.put("errMsg", "Failed to communicate: " + e.getMessage());

    } finally {
        // ✅ Reset timeout
        Unirest.setTimeouts(5000, 10000);
    }

    return result;
}
```

**ผลลัพธ์**:
- Timeout 3s + 5s = max 8 วินาที (แทนที่จะรอไม่รู้จบ)
- Circuit breaker skip sites ที่ล่มติดต่อกัน
- ประหยัดเวลา 80-90%

---

### **Solution 3: Batch WebSocket Updates** 🟡

#### **ปัญหาเดิม**:
```java
// ❌ ส่ง WebSocket ทุก site ที่ update
wsAnnounceCheckSite(site, true);  // ส่งทีละ site
```

#### **วิธีแก้**:
```java
// ✅ Batch WebSocket messages (ส่งรวม 5 นาทีครั้ง)
private List<SiteStatusUpdate> pendingUpdates = Collections.synchronizedList(new ArrayList<>());

public void checkSiteStatus() {
    // ... check sites ...

    // เก็บ updates ไว้ก่อน
    offlineSites.forEach(site -> {
        pendingUpdates.add(new SiteStatusUpdate(site.getCode(), false, LocalDateTime.now()));
    });

    // ส่งรวมกันทุก 5 นาที หรือเมื่อมี 100 updates
    if (pendingUpdates.size() >= 100 || shouldFlushUpdates()) {
        flushWebSocketUpdates();
    }
}

@Scheduled(fixedRate = 300000)  // ทุก 5 นาที
private void flushWebSocketUpdates() {
    if (!pendingUpdates.isEmpty()) {
        JSONArray updates = new JSONArray();
        pendingUpdates.forEach(update -> updates.put(update.toJSON()));

        messagingTemplate.convertAndSend("/topic/site_status_batch", updates.toString());

        pendingUpdates.clear();
        log.info("Flushed {} site status updates via WebSocket", updates.length());
    }
}
```

**ผลลัพธ์**:
- ลด WebSocket messages จาก หลักร้อย → 1 message/5นาที
- ลด network bandwidth
- Frontend ประมวลผลง่ายขึ้น

---

### **Solution 4: Conditional Execution (ไม่ทำงานถ้าไม่จำเป็น)**

#### **reportAlarmLogToFile**:
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
        return;  // ข้ามไป ถ้าไม่มี logs ใหม่
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
```

**ผลลัพธ์**:
- ถ้าไม่มี alarm logs ใหม่ → ข้ามไปเลย
- ประหยัด disk I/O

---

### **Solution 5: ใช้ @Async อย่างถูกต้อง**

#### **ปัญหาที่พบ**:
```java
// ❌ @Transactional + @Async = อันตราย!
@Async
@Scheduled(cron = "30 */1 * * * *")
@Transactional  // Transaction ยาวเกินไป!
public void reportAlarmLogToFile() {
    ...
}
```

**เหตุผล**: `@Transactional` + `@Async` → Transaction ไม่ commit จนกว่า async task จะเสร็จ → Connection leak!

#### **วิธีแก้**:
```java
// ✅ แยก Transaction ออกจาก Async
@Scheduled(cron = "30 */1 * * * *")  // ไม่ใส่ @Async ที่ scheduler
public void reportAlarmLogToFile() {
    String site_scheduler = env.getProperty("app.site_scheduler");
    if (site_scheduler.equals("no")) {
        return;
    }

    // ✅ Query ภายนอก transaction
    List<LogDataModel> alarmLogList = alarmLogCasService.getAllUnReportedAlarm();

    if(alarmLogList.size() > 0){
        // ✅ เรียก @Async method แยกต่างหาก
        writeAlarmFileAsync(alarmLogList);
    }
}

@Async  // Async ที่นี่
private void writeAlarmFileAsync(List<LogDataModel> alarmLogList) {
    try {
        alarmSubmit.writeAlarmFile3(alarmLogList);
    } catch (FileNotFoundException e) {
        log.error("Please create alarm log directory !!!");
    }
}
```

---

## 📊 สรุปผลลัพธ์ที่คาดหวัง

| Metric | Before | After | Improvement |
|--------|--------|-------|-------------|
| **checkSiteStatus** | ทุก 1 นาที (11,520/วัน) | ทุก 5 นาที (2,880/วัน) | **75% ลดลง** |
| **reportAlarmLogToFile** | ทุก 1 นาที (11,520/วัน) | ทุก 10 นาที (1,152/วัน) | **90% ลดลง** |
| **generateEmergencyKey** | ทุก 2 นาที (720/วัน) | ทุก 30 นาที (48/วัน) | **93% ลดลง** |
| **HTTP Calls (Awaken)** | ไม่มี timeout | 3s + 5s = max 8s | **80% เร็วขึ้น** |
| **Circuit Breaker** | ❌ ไม่มี | ✅ มี | **Skip failing sites** |
| **WebSocket Messages** | หลักร้อย/นาที | 1 batch/5นาที | **95% ลดลง** |
| **Database Queries** | ~12,000/ชม | ~600/ชม | **95% ลดลง** |
| **Disk I/O** | 60 ครั้ง/ชม | 6 ครั้ง/ชม | **90% ลดลง** |

---

## 🚀 แผนการแก้ไข (เรียงตาม Priority)

### **Phase 1: ลดความถี่ Schedulers (เร่งด่วน)** ⚠️

1. ✅ เพิ่ม config ใน `node1.properties`:
```properties
# Scheduler Frequencies - Optimized for performance
app.check_site_status_cron=0 */5 * * * *           # ทุก 5 นาที (จากเดิม 1 นาที)
app.report_alarm_log_cron=0 */10 * * * *           # ทุก 10 นาที (จากเดิม 1 นาที)
app.generate_emergency_key_cron=0 */30 * * * *     # ทุก 30 นาที (จากเดิม 2 นาที)
```

2. ✅ แก้ไข Code:
   - เปลี่ยน `@Scheduled` จาก hardcode เป็น configurable
   - เพิ่ม conditional checks

---

### **Phase 2: เพิ่ม Timeout และ Circuit Breaker** ⚠️

1. ✅ เพิ่ม timeout ให้ `CallAwakenSite()`
2. ✅ เพิ่ม circuit breaker check
3. ✅ เพิ่ม timeout ให้ `callHealthCheck()`

---

### **Phase 3: Batch WebSocket Updates** 🟡

1. ✅ เก็บ updates ไว้ใน memory
2. ✅ ส่งรวมกันทุก 5 นาที
3. ✅ Frontend รับ batch แทนทีละ message

---

### **Phase 4: Database Query Optimization** 🟡

1. ✅ เพิ่ม index สำหรับ queries ที่ใช้บ่อย
2. ✅ ใช้ batch updates แทน individual saves
3. ✅ Cache ข้อมูลที่ไม่ค่อยเปลี่ยน

---

## 📋 Checklist สำหรับ Production

**ก่อน Deploy**:
- [ ] Backup config เดิม
- [ ] ทดสอบ cron expressions ให้ถูกต้อง
- [ ] ตรวจสอบ circuit breaker ทำงาน

**หลัง Deploy**:
- [ ] Monitor CPU usage (ควรลดลง 30-50%)
- [ ] Monitor database queries (ควรลดลง 90%)
- [ ] Monitor logs - ดูว่า schedulers ทำงานตาม interval ใหม่
- [ ] เช็ค WebSocket traffic (ควรลดลง 95%)

---

**สร้างโดย**: Claude Code Analysis
**วันที่**: 2025-12-18
**สถานะ**: Ready for Implementation ✅
