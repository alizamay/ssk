# คู่มือการเปลี่ยน TTSM Integration จาก Push เป็น Pull/Webhook

## 📋 สรุปการเปลี่ยนแปลง

เปลี่ยนจากระบบที่ **เราเรียก TTSM** (Push) เป็น **TTSM เรียกเรา** (Pull/Webhook)

### ก่อนหน้า (Push Model)
```
SmartSite App → POST → TTSM API
   - submitToTTSM (SiteService.java:582)
   - submitEmergencyKey (SiteScheduler.java:562)
   - askTTSMOnSite (AlarmLogService.java:388)
```

### ปัจจุบัน (Pull/Webhook Model)
```
TTSM → GET → SmartSite App (ดึงข้อมูล Site Master)
TTSM → GET → SmartSite App (ดึงข้อมูล Emergency Keys)
TTSM → POST → SmartSite App (ส่ง Job Status มาให้)
```

---

## 🎯 ไฟล์ที่สร้างใหม่

### 1. Controller
- **TtsmWebhookController.java** - รับ request จาก TTSM
  - `GET /api/ttsm/webhook/site-master/{siteCode}` - TTSM ดึงข้อมูล Site Master
  - `GET /api/ttsm/webhook/emergency-keys` - TTSM ดึง Emergency Keys
  - `POST /api/ttsm/webhook/job-status` - TTSM ส่ง Job Status มาให้

### 2. DTO (Data Transfer Object)
- **TtsmSiteMasterDto.java** - ข้อมูล Site Master สำหรับส่งให้ TTSM
- **TtsmJobStatusDto.java** - ข้อมูล Job Status ที่ TTSM ส่งมา

### 3. Model (Database Entity)
- **TtsmJobStatus.java** - Entity สำหรับเก็บ Job Status จาก TTSM

### 4. Repository
- **TtsmJobStatusRepository.java** - Repository สำหรับจัดการข้อมูล Job Status

### 5. Service
- **TtsmWebhookService.java** - Business logic สำหรับจัดการข้อมูล TTSM
- **AlarmLogServiceRefactored.java** - ตัวอย่างการแก้ไขโค้ดเดิม

---

## 📊 Database Schema

### Table: `ttsm_job_status`

```sql
CREATE TABLE ttsm_job_status (
    id BIGSERIAL PRIMARY KEY,
    site_code VARCHAR(50) NOT NULL,
    job_id VARCHAR(100),
    job_type VARCHAR(50),
    job_status VARCHAR(50),
    is_onsite INTEGER,  -- 0=Unknown, 1=Onsite, 2=Not onsite
    technician_name VARCHAR(200),
    technician_phone VARCHAR(50),
    job_start_time TIMESTAMP,
    job_end_time TIMESTAMP,
    received_at TIMESTAMP NOT NULL,
    remarks VARCHAR(500)
);

-- Indexes
CREATE INDEX idx_site_code ON ttsm_job_status(site_code);
CREATE INDEX idx_job_id ON ttsm_job_status(job_id);
CREATE INDEX idx_job_status ON ttsm_job_status(job_status);
CREATE INDEX idx_received_at ON ttsm_job_status(received_at);
```

---

## 🔄 การเปลี่ยนแปลงใน Properties

### ไม่ต้องใช้แล้ว (สามารถเก็บไว้ได้)
```properties
# ไม่ต้องใช้แล้วเพราะ TTSM จะเรียกมาหาเรา
ttsm_endpoint_update_master=TTSWebService/rest/ttsWs/updateMasterSmartSite
ttsm_endpoint_update_emerg_key=TTSWebService/rest/ttsWs/updateMasterSmartSiteEmergency
ttsm_endpoint_check_onsite=TTSWebService/rest/ttsWs/getJobTTOnSiteBySite
```

### ยังคงใช้
```properties
# ใช้สำหรับตรวจสอบ API Key ที่ TTSM ส่งมา
apikey_by_ttsm=your_api_key_here
```

---

## 🔌 API Endpoints สำหรับ TTSM

### 1. Pull Site Master Data
**TTSM เรียก**: ดึงข้อมูล Site Master

```http
GET /api/ttsm/webhook/site-master/{siteCode}
Headers:
  apiKey: {apikey_by_ttsm}

Response:
{
  "siteCode": "BKK001",
  "siteName": "Site Bangkok 1",
  "siteAddress": "123 Sukhumvit Rd.",
  "latitude": "13.736717",
  "longitude": "100.523186",
  "province": "กรุงเทพมหานคร",
  "district": "วัฒนา",
  "subDistrict": "คลองเตย",
  "emergencyKey": "ABC123XYZ",
  "emergencyKeyUpdatedAt": "2025-02-17 10:30:00",
  "status": "ONLINE",
  "lastUpdated": "2025-02-17 11:00:00"
}
```

### 2. Pull Emergency Keys
**TTSM เรียก**: ดึง Emergency Keys ทั้งหมด

```http
GET /api/ttsm/webhook/emergency-keys
Headers:
  apiKey: {apikey_by_ttsm}

Query Params (optional):
  siteCode=BKK001  (ถ้าต้องการเฉพาะ site)

Response:
[
  {
    "siteCode": "BKK001",
    "siteName": "Site Bangkok 1",
    "emergencyKey": "ABC123XYZ",
    "emergencyKeyUpdatedAt": "2025-02-17 10:30:00"
  },
  ...
]
```

### 3. Receive Job Status (Webhook)
**TTSM ส่งมา**: แจ้ง Job Status

```http
POST /api/ttsm/webhook/job-status
Headers:
  apiKey: {apikey_by_ttsm}
  Content-Type: application/json

Body:
{
  "siteCode": "BKK001",
  "jobId": "JOB20250217001",
  "jobType": "TT_ONSITE",
  "jobStatus": "IN_PROGRESS",
  "isOnsite": 1,
  "technicianName": "สมชาย ใจดี",
  "technicianPhone": "0812345678",
  "jobStartTime": "2025-02-17T08:00:00",
  "jobEndTime": null,
  "remarks": "กำลังเข้าไปตรวจสอบ site"
}

Response:
{
  "success": true,
  "message": "Job status received"
}
```

---

## 💡 วิธีใช้งานในโค้ด

### ❌ เดิม (เรียก TTSM API)

```java
// AlarmLogService.java:388
public ResponseEntity<OnsiteResponse> askTTSMOnSite(SiteStatusRequest request) {
    // POST ไปหา TTSM
    HttpResponse<String> response = Unirest.post(targetUrl)
        .header("apiKey", apiKeyByTTSM)
        .body(jsonBody)
        .asString();
    // ...
}
```

### ✅ ใหม่ (อ่านจาก Database)

```java
// AlarmLogServiceRefactored.java
@Autowired
private TtsmWebhookService ttsmWebhookService;

public ResponseEntity<OnsiteResponse> checkTechnicianOnsite(SiteStatusRequest request) {
    // อ่านจาก database ที่ TTSM ส่งมาให้
    TtsmJobStatus latestJob = ttsmWebhookService.getLatestJobStatus(siteCode);

    if (latestJob != null && latestJob.getIsOnsite() == 1) {
        // มีช่าง onsite
    }
    // ...
}
```

### ตัวอย่างการใช้งาน

```java
// เช็คว่ามีช่าง onsite หรือไม่
boolean hasOnsite = ttsmWebhookService.isTechnicianOnsite("BKK001");

// ดึง Active Jobs ทั้งหมด
List<TtsmJobStatus> activeJobs = ttsmWebhookService.getActiveJobs("BKK001");

// ดึง Job ล่าสุด
TtsmJobStatus latest = ttsmWebhookService.getLatestJobStatus("BKK001");
```

---

## 🔐 Security

### API Key Validation
ทุก request จาก TTSM จะถูกตรวจสอบ API Key ผ่าน header:

```java
@RequestHeader(value = "apiKey", required = false) String apiKey
```

หาก API Key ไม่ถูกต้อง จะ return:
```json
{
  "success": false,
  "message": "Invalid API Key"
}
```
HTTP Status: `401 Unauthorized`

---

## 🧹 Data Cleanup

### ลบข้อมูล Job Status เก่า

สร้าง Scheduler สำหรับทำความสะอาดข้อมูลเก่า:

```java
@Scheduled(cron = "0 0 2 * * *") // ทุกวัน 02:00 น.
public void cleanupOldJobStatus() {
    ttsmWebhookService.cleanupOldJobStatus(30); // เก็บไว้ 30 วัน
}
```

---

## 📝 ขั้นตอนการ Deploy

### 1. สร้าง Database Table
```sql
-- รัน SQL script เพื่อสร้าง table ttsm_job_status
-- (ดู Database Schema ด้านบน)
```

### 2. Update Application Properties
```properties
# ตรวจสอบว่ามี apikey_by_ttsm
apikey_by_ttsm=your_secret_key_here
```

### 3. Update Security Config
เพิ่ม endpoint ที่ TTSM จะเรียกเข้ามาใน SecurityConfig:

```java
.antMatchers("/api/ttsm/webhook/**").permitAll()
```

### 4. แจ้ง TTSM ทีม
แจ้ง TTSM ทีมเกี่ยวกับ:
- **URL endpoints** ที่ต้องเรียก
- **API Key** สำหรับยืนยันตัวตน
- **Format ของข้อมูล** (ตาม DTO ที่กำหนด)
- **Webhook URL** สำหรับส่ง Job Status

---

## 🎬 สรุปข้อดีของการเปลี่ยนแปลง

### ✅ ข้อดี

1. **ลดการเรียก API ไม่จำเป็น**
   - เดิม: เรียก TTSM ทุกครั้งที่ต้องการข้อมูล
   - ใหม่: อ่านจาก database (เร็วกว่า)

2. **ลด Dependency**
   - เดิม: ถ้า TTSM API ล่ม → ระบบเราก็มีปัญหา
   - ใหม่: มีข้อมูลใน database → ยังใช้งานได้

3. **Real-time Data**
   - TTSM ส่งข้อมูล Job Status แบบ real-time ผ่าน webhook

4. **Performance**
   - อ่าน database เร็วกว่าการเรียก HTTP API

### ⚠️ ข้อควรระวัง

1. **Data Synchronization**
   - ต้องทำให้แน่ใจว่า TTSM ส่งข้อมูลมาทันเวลา

2. **Database Size**
   - ต้องมี cleanup schedule เพื่อลบข้อมูลเก่า

3. **TTSM Integration**
   - TTSM ต้องปรับระบบของพวกเขาให้เรียก API ของเรา

---

## 📞 Contact

หากมีคำถามเพิ่มเติม ติดต่อ Development Team
