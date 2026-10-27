# Runbook: DB connection pool เต็ม / OOM บน appserver (ต.ค. 2026)

สำหรับทีม ops ที่ deploy และดูแล WildFly ทั้ง 8 node (PSSMA801G–804G × node1/node2)

## สรุปปัญหาจาก log (02–08 ต.ค. 2026)

| อาการ | หลักฐาน | สาเหตุ |
|---|---|---|
| PSSMA802G node1 ตาย 10-02 06:42 | `OutOfMemoryError: unable to create new native thread`, thread ค้าง 26,5xx ตัว | `@Async` ใช้ `SimpleAsyncTaskExecutor` สร้าง thread ใหม่ทุกงาน |
| PSSMA802G node2 ตาย 10-05 | pool error 11,989 ครั้ง, JVM หยุดนิ่งนานสุด 7 นาที, `Java heap space` | `HighThroughputStatusService` เก็บ status report ทุกอันไว้ใน queue ไม่มีวันลด |
| ทุก node | `Queue:` ใน `STATUS_PERF` โต ~1.1 ล้าน/วัน/node | worker thread ของ service ข้างบน deadlock ตั้งแต่เริ่ม |
| site ค้าง online | ไม่มี scheduler leader ตั้งแต่ 10-02 06:42 | node ที่รัน scheduler (802G) ตายทั้งคู่ |

WAR ที่รันอยู่บน prod **เก่ากว่าโค้ดใน repo**: log ไม่มี thread ชื่อ `async-default-` เลย แต่ยังเห็น `SimpleAsyncTaskExecutor-N` แปลว่าการแก้ thread รั่วใน repo ยังไม่ได้ deploy

## 1. ก่อน deploy

1. ตรวจ `max_connections` ของ PostgreSQL:
   ```sql
   SHOW max_connections;
   SELECT count(*) FROM pg_stat_activity;
   ```
   ผลรวมของ `maximumPoolSize` ทุก node + connection ของเครื่องมืออื่น ต้องน้อยกว่าค่านี้ (เว้นไว้อย่างน้อย 10%)
2. ตรวจ limit ของ user ที่รัน WildFly: `ulimit -u` (max user processes) ควร ≥ 4096
3. ตรวจ heap ปัจจุบันใน `standalone.conf`: `-Xms` / `-Xmx`

## 2. ค่าที่ต้องใส่ในไฟล์ properties ของแต่ละ node

ไฟล์ config ภายนอกของ node (ไฟล์ `PSSMA80xG_nodeN.properties`) override ค่าใน WAR ปัจจุบันตั้ง `maximumPoolSize=5` (802G node1 = 20)

ตัวอย่างเมื่อ `max_connections = 100` (8 node):

```properties
# node ที่รัน scheduler (app.site_scheduler=yes): PSSMA802G node1/node2
spring.datasource.hikari.maximumPoolSize=15
spring.datasource.hikari.minimumIdle=5
# node อื่น 6 node
# spring.datasource.hikari.maximumPoolSize=8
# spring.datasource.hikari.minimumIdle=2

spring.datasource.hikari.connectionTimeout=10000
spring.datasource.hikari.leakDetectionThreshold=30000
spring.datasource.hikari.maxLifetime=900000
```

รวม = 15×2 + 8×6 = 78 connection ถ้า `max_connections` มากกว่านี้ค่อยเพิ่มตามสัดส่วน **อย่าตั้ง 100 ต่อ node**

## 3. JVM options (`standalone.conf`)

```
-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/var/log/wildfly/heapdump
-XX:+ExitOnOutOfMemoryError
```

`ExitOnOutOfMemoryError` ทำให้ JVM ปิดตัวเองแทนที่จะค้างครึ่งๆ กลางๆ ต้องมี service manager (systemd ฯลฯ) สั่ง restart ให้

## 4. ตรวจหลัง deploy (ภายใน 30 นาที)

ใน `server.log` ของแต่ละ node:

| ต้องเจอ | ต้องไม่เจอ |
|---|---|
| `Default async executor initialized` | `STATUS_PERF`, `QUEUE_BACKLOG`, `LOW_THROUGHPUT` |
| thread ชื่อ `async-default-N` | thread ชื่อ `SimpleAsyncTaskExecutor-N` |
| `MEMORY_MONITOR | Heap: 812.3MB/4096.0MB (19.8%)` (มีตัวเลขจริง) | `HikariPool ... Connection is not available` |
| `THREAD_MONITOR | Active:` คงที่ไม่เกิน ~300 | `Apparent connection leak detected` ต่อเนื่อง |

node ที่รัน scheduler ต้องมี log ว่าได้ leader lock และมี `checkSiteStatus` ทำงานทุกนาที

## 5. ถ้ามีปัญหาหลัง deploy (rollback แบบไม่ต้องเปลี่ยน WAR)

การแก้รอบนี้ปิด `open-in-view` ถ้ามีหน้าจอไหน error เรื่อง `LazyInitializationException` ใส่ค่านี้ในไฟล์ properties ของ node แล้ว restart:

```properties
spring.jpa.open-in-view=true
```

## 6. งานฝั่ง DB (ให้ DBA ตรวจก่อนรัน)

ดู `docs/sql/2026-10-27-proposed-indexes.sql` เป็นข้อเสนอ index ที่ขาดสำหรับ query ของ alarm และ TTSM ยังไม่ได้รันบน prod
