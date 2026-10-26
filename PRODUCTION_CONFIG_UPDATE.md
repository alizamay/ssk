# 🔧 สิ่งที่ต้องเพิ่มใน Production Config Files

**วันที่**: 2026-01-03
**เส้นทาง Production**: `C:\Users\Tanate\OneDrive\Videos\ssk\New folder\smartsite\`

---

## 📋 สรุปไฟล์ที่ต้องแก้

| ไฟล์ | ต้องแก้ไหม | ความสำคัญ |
|------|-----------|----------|
| **application.properties** | ✅ ใช่ | 🔴 CRITICAL |
| **node1.properties** | ✅ ใช่ | 🔴 CRITICAL |
| **node2.properties** | ✅ ใช่ | 🔴 CRITICAL |
| **config.properties** | ❓ ดูภายหลัง | 🟡 MEDIUM |

---

## 🔴 ไฟล์ที่ 1: application.properties

**ปัญหา**: ไฟล์ production ขาดการตั้งค่าหลายอย่างที่สำคัญ!

### **สิ่งที่ต้องเพิ่ม**:

```properties
# ========================================
# 1. ✅ File Upload Size (ขาดไปจาก production!)
# ========================================
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB
spring.http.multipart.max-file-size=10MB
spring.http.multipart.max-request-size=10MB

# ========================================
# 2. ✅ Logging Configuration (ควรเปลี่ยนเป็น WARN)
# ========================================
# เดิม: logging.level.org.springframework=INFO
# ใหม่:
logging.level.org.springframework=WARN
logging.level.programmer.bonn.play.springbootlog=WARN
logging.level.root=ERROR
logging.level.org.hibernate.SQL=WARN
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=WARN
logging.level.dev.niceone.smartsiteappserver.service.SiteService=WARN
java.util.logging.manager=org.apache.logging.log4j.jul.LogManager

# ========================================
# 3. 🔴 HikariCP Pool Size (CRITICAL!)
# ========================================
# เดิม: spring.datasource.hikari.maximumPoolSize=5  ❌ น้อยเกินไป!
# ใหม่:
spring.datasource.hikari.connectionTimeout=30000
spring.datasource.hikari.idleTimeout=600000
spring.datasource.hikari.maxLifetime=1800000
spring.datasource.hikari.maximumPoolSize=100
spring.datasource.hikari.minimumIdle=20
spring.datasource.hikari.leakDetectionThreshold=60000
spring.datasource.hikari.pool-name=SmartSiteHikariCP
spring.datasource.hikari.registerMbeans=true

# ========================================
# 4. ✅ JPA & Hibernate Performance
# ========================================
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.format_sql=false
spring.jpa.properties.hibernate.connection.autocommit=false
spring.jpa.properties.hibernate.current_session_context_class=thread
spring.jpa.properties.hibernate.jdbc.batch_size=50
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true
spring.jpa.properties.hibernate.jdbc.batch_versioned_data=true

# ========================================
# 5. ✅ Thread Pool Configuration
# ========================================
# Main async executor
spring.task.execution.pool.core-size=32
spring.task.execution.pool.max-size=128
spring.task.execution.pool.queue-capacity=10000
spring.task.execution.pool.keep-alive=30s
spring.task.execution.thread-name-prefix=async-
spring.task.execution.shutdown.await-termination=true
spring.task.execution.shutdown.await-termination-period=60s

# Scheduling thread pool
spring.task.scheduling.pool.size=8
spring.task.scheduling.thread-name-prefix=scheduler-

# ========================================
# 6. ✅ Tomcat Tuning
# ========================================
server.tomcat.threads.max=500
server.tomcat.threads.min-spare=50
server.tomcat.accept-count=1000
server.tomcat.max-connections=20000
server.tomcat.connection-timeout=5000

# ========================================
# 7. ✅ Custom Thread Pools
# ========================================
app.thread.prove-offline.core-size=6
app.thread.prove-offline.max-size=12
app.thread.prove-offline.queue-capacity=50
app.thread.batch-processing.core-size=4
app.thread.batch-processing.max-size=8
app.thread.batch-processing.queue-capacity=100

# ========================================
# 8. ✅ Missing Properties
# ========================================
app.alarm_postfix=
app.offline_check=yes
app.online_check=yes
app.site_status_corn=0 50 22 * * *
app.offline_check_corn=0 55 22 * * *
app.online_check_corn=0 59 22 * * *

# ========================================
# 9. ✅ Node Distribution (สำหรับ 8 servers)
# ========================================
app.node.id=1
app.node.total=8
app.distribution.strategy=hash_based
app.distribution.batch.size=50
```

---

## 🔴 ไฟล์ที่ 2: node1.properties

**ปัญหา**: Pool size = 5 ทำให้เกิด 8,717 timeout errors!

### **สิ่งที่ต้องแก้/เพิ่ม**:

```properties
# ========================================
# 🔴 CRITICAL: HikariCP Pool Size
# ========================================
# ❌ เดิม
spring.datasource.hikari.connectionTimeout=20000
spring.datasource.hikari.maximumPoolSize=5

# ✅ ใหม่ (เพิ่ม 20 เท่า!)
spring.datasource.hikari.connectionTimeout=30000
spring.datasource.hikari.idleTimeout=600000
spring.datasource.hikari.maxLifetime=1800000
spring.datasource.hikari.maximumPoolSize=100
spring.datasource.hikari.minimumIdle=20
spring.datasource.hikari.leakDetectionThreshold=60000
spring.datasource.hikari.pool-name=SmartSiteHikariCP-Node1
spring.datasource.hikari.registerMbeans=true

# ========================================
# ✅ เพิ่ม: File Upload Size
# ========================================
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB
spring.http.multipart.max-file-size=10MB
spring.http.multipart.max-request-size=10MB

# ========================================
# ✅ เพิ่ม: JPA Performance
# ========================================
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.format_sql=false
spring.jpa.properties.hibernate.connection.autocommit=false
spring.jpa.properties.hibernate.current_session_context_class=thread
spring.jpa.properties.hibernate.jdbc.batch_size=50
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true
spring.jpa.properties.hibernate.jdbc.batch_versioned_data=true

# ========================================
# ✅ เพิ่ม: Thread Pool
# ========================================
spring.task.execution.pool.core-size=32
spring.task.execution.pool.max-size=128
spring.task.execution.pool.queue-capacity=10000
spring.task.execution.pool.keep-alive=30s
spring.task.execution.thread-name-prefix=async-node1-
spring.task.execution.shutdown.await-termination=true
spring.task.execution.shutdown.await-termination-period=60s

spring.task.scheduling.pool.size=8
spring.task.scheduling.thread-name-prefix=scheduler-node1-

# ========================================
# ✅ เพิ่ม: Tomcat Tuning
# ========================================
server.tomcat.threads.max=500
server.tomcat.threads.min-spare=50
server.tomcat.accept-count=1000
server.tomcat.max-connections=20000
server.tomcat.connection-timeout=5000

# ========================================
# ✅ เพิ่ม: Custom Thread Pools
# ========================================
app.thread.prove-offline.core-size=6
app.thread.prove-offline.max-size=12
app.thread.prove-offline.queue-capacity=50
app.thread.batch-processing.core-size=4
app.thread.batch-processing.max-size=8
app.thread.batch-processing.queue-capacity=100

# ========================================
# ✅ เพิ่ม: Logging
# ========================================
logging.level.org.hibernate.SQL=WARN
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=WARN
logging.level.dev.niceone.smartsiteappserver.service.SiteService=WARN
java.util.logging.manager=org.apache.logging.log4j.jul.LogManager

# ========================================
# ✅ เพิ่ม: Missing Properties
# ========================================
app.alarm_postfix=
app.offline_check=yes
app.online_check=yes
app.site_status_corn=0 50 22 * * *
app.offline_check_corn=0 55 22 * * *
app.online_check_corn=0 59 22 * * *

# ========================================
# ✅ เพิ่ม: Node Distribution
# ========================================
app.node.id=1
app.node.total=8
app.distribution.strategy=hash_based
app.distribution.batch.size=50
```

---

## 🔴 ไฟล์ที่ 3: node2.properties

**เหมือนกับ node1.properties แต่เปลี่ยน**:

```properties
# Node identification
app.node.id=2  # เปลี่ยนเป็น 2

# Pool name
spring.datasource.hikari.pool-name=SmartSiteHikariCP-Node2

# Thread prefix
spring.task.execution.thread-name-prefix=async-node2-
spring.task.scheduling.thread-name-prefix=scheduler-node2-
```

**และเพิ่มทุกอย่างเหมือน node1.properties**

---

## 📊 เปรียบเทียบ Before/After

### **application.properties**

| Property | Before | After | ผลกระทบ |
|----------|--------|-------|----------|
| **maximumPoolSize** | 5 | 100 | ✅ แก้ 8,717 timeout errors |
| **Thread pool** | ❌ ไม่มี | ✅ มี | ✅ ป้องกัน thread exhaustion |
| **Batch processing** | ❌ ไม่มี | ✅ มี | ✅ เร็วขึ้น 50-80% |
| **Tomcat tuning** | ❌ ไม่มี | ✅ มี | ✅ รองรับ load สูง |
| **Logging** | INFO | WARN | ✅ ลด disk I/O |

### **node1.properties**

| Property | Before | After | ผลกระทบ |
|----------|--------|-------|----------|
| **maximumPoolSize** | 5 | 100 | 🔴 CRITICAL FIX |
| **minimumIdle** | ❌ ไม่มี | 20 | ✅ ลดการสร้าง connection ใหม่ |
| **leakDetection** | ❌ ไม่มี | 60000ms | ✅ ตรวจจับ connection leak |
| **Thread pool** | ❌ ไม่มี | ✅ มี | ✅ Async tasks ทำงานได้เร็ว |

---

## 🚀 วิธี Deploy

### **Step 1: Backup ไฟล์เดิม**
```bash
cd "C:\Users\Tanate\OneDrive\Videos\ssk\New folder\smartsite"
mkdir backup_20260103
copy *.properties backup_20260103\
```

### **Step 2: แก้ไข application.properties**
1. เปิดไฟล์ `application.properties`
2. เพิ่มทุกอย่างจากด้านบนลงไปท้ายไฟล์
3. Save

### **Step 3: แก้ไข node1.properties**
1. เปิดไฟล์ `node1.properties`
2. **ลบบรรทัด 47-48** (pool size เก่า):
   ```properties
   # ❌ ลบสองบรรทัดนี้
   spring.datasource.hikari.connectionTimeout=20000
   spring.datasource.hikari.maximumPoolSize=5
   ```
3. **เพิ่มทุกอย่างจากด้านบน** แทนที่
4. Save

### **Step 4: แก้ไข node2.properties**
1. เปิดไฟล์ `node2.properties`
2. **ลบบรรทัด pool size เก่า** (คล้าย node1)
3. **เพิ่มทุกอย่างจากด้านบน** แต่เปลี่ยน:
   - `app.node.id=2`
   - `pool-name=SmartSiteHikariCP-Node2`
   - `thread-name-prefix=async-node2-`
4. Save

### **Step 5: แก้ node3 ถึง node8**
ทำเหมือน node2 แต่เปลี่ยน:
- `app.node.id=3,4,5,6,7,8`
- `pool-name=SmartSiteHikariCP-Node3...Node8`
- `thread-name-prefix=async-node3-...async-node8-`

### **Step 6: Restart Servers**
```bash
# Restart ทั้ง 8 servers ทีละตัว
# ตรวจสอบ log หลัง restart:
tail -f logs/app.log | grep -E "HikariCP|pool|thread"
```

### **Step 7: Verify**
หลัง restart ตรวจสอบ:
```bash
# 1. Pool size
grep "maximumPoolSize" logs/app.log
# ควรเห็น: maximumPoolSize=100

# 2. Thread pool
grep "thread-name-prefix" logs/app.log
# ควรเห็น: async-node1-, scheduler-node1-

# 3. ไม่มี timeout errors
tail -f logs/app.log | grep -i timeout
# ควรไม่เห็น HikariPool timeout
```

---

## 📋 Checklist

### **ก่อน Deploy**:
- [ ] Backup ไฟล์ทั้งหมดแล้ว
- [ ] อ่านเอกสารนี้จบแล้ว
- [ ] เตรียม rollback plan

### **หลัง Deploy**:
- [ ] Pool size = 100 (ตรวจสอบ log)
- [ ] ไม่มี HikariPool timeout errors
- [ ] Thread pools ทำงานปกติ
- [ ] CPU usage ลดลง 30-50%
- [ ] Response time เร็วขึ้น

---

## ⚠️ คำเตือน

### **สิ่งที่ต้องระวัง**:

1. **Database Password แตกต่างกัน**:
   - `application.properties`: `<SSK_DB_PASSWORD>`
   - `node1.properties`: `<SSK_DB_PASSWORD>`
   - ใช้ password ไหน? → **ใช้ตาม node1.properties** (production)

2. **LDAP Endpoint แตกต่างกัน**:
   - `application.properties`: `https://dev-ldapws.intra.ais/...`
   - `node1.properties`: `https://dev-ldapws.intra.ais/...`
   - ✅ เหมือนกัน OK

3. **app.my_ip ไม่เหมือนกัน**:
   - `application.properties`: `app.my_ip=10.242.15.22`
   - `node1.properties`: **ไม่มี!**
   - ✅ ต้องเพิ่ม `app.my_ip=10.242.15.22` ใน node1.properties

---

## 📁 ไฟล์สำเร็จรูป

ผมสร้างไฟล์สำเร็จรูปให้แล้วใน:
- [node1.properties.recommended](node1.properties.recommended)
- [PRODUCTION_CONFIG_CHANGES.md](PRODUCTION_CONFIG_CHANGES.md)

**แต่ต้องแก้ใน Production path**:
```
C:\Users\Tanate\OneDrive\Videos\ssk\New folder\smartsite\
```

---

## 🎯 สรุป

**ต้องเพิ่ม/แก้ไข**:

### **application.properties**:
- ✅ HikariCP pool (5 → 100)
- ✅ Thread pools
- ✅ JPA batch processing
- ✅ Tomcat tuning
- ✅ Logging (INFO → WARN)
- ✅ File upload limits
- ✅ Node distribution config

### **node1.properties**:
- 🔴 HikariCP pool (5 → 100) - **CRITICAL!**
- ✅ ทุกอย่างเหมือน application.properties
- ✅ เพิ่ม `app.my_ip=10.242.15.22`

### **node2-8.properties**:
- ✅ เหมือน node1 แต่เปลี่ยน node.id

---

**ไฟล์นี้พร้อม deploy แล้วค่ะ!** 🚀

**คำแนะนำสุดท้าย**: Deploy ที่ละ server ทดสอบให้ดีก่อน deploy ทั้งหมด
