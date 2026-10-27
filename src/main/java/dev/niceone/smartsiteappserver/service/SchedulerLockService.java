package dev.niceone.smartsiteappserver.service;

import dev.niceone.smartsiteappserver.repository.SchedulerLockRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class SchedulerLockService {

    // แยกชื่อ lock ได้ เมื่อหลาย environment (เช่น UAT กับเครื่อง dev) ใช้ DB เดียวกัน
    @Value("${app.scheduler.lock_name:main_scheduler}")
    private String lockName = "main_scheduler";
    // lock expire ถ้าไม่มี heartbeat นาน 120 วินาที (4 รอบ heartbeat)
    private static final int LOCK_EXPIRE_SEC = 120;
    // node นี้ถือว่าตัวเองเป็น leader ได้ไม่เกิน 90 วินาทีหลัง renew สำเร็จครั้งล่าสุด
    // (น้อยกว่า LOCK_EXPIRE_SEC 30 วินาที) เพื่อให้หยุดทำงานก่อนที่ node อื่นจะยึด lock ได้
    private static final long LEASE_NANOS = TimeUnit.SECONDS.toNanos(LOCK_EXPIRE_SEC - 30);

    // UUID unique ต่อ process — generate ครั้งเดียวตอน startup
    private final String nodeId = UUID.randomUUID().toString();

    // System.nanoTime() ที่ leadership หมดอายุ; 0 = ไม่ได้เป็น leader
    // เดิมเป็น boolean ที่ค้าง true ได้ไม่จำกัด ถ้า heartbeat หยุด (GC pause, รอ connection)
    // ทำให้ 2 node ทำงาน scheduler พร้อมกันหลัง node อื่นยึด lock ไปแล้ว
    private volatile long leaderUntilNanos = 0;

    @Autowired
    private SchedulerLockRepository repository;

    public String getNodeId() {
        return nodeId;
    }

    public boolean isLeader() {
        long until = leaderUntilNanos;
        return until != 0 && System.nanoTime() - until < 0;
    }

    /**
     * พยายามยึด lock หรือ renew ถ้าถือ lock อยู่แล้ว
     * เรียกทุก 30 วินาทีจาก heartbeat thread ของ SiteScheduler
     */
    @Transactional(noRollbackFor = Exception.class)
    public boolean tryAcquireOrRenew() {
        // นับ lease จากก่อนส่ง query (ฝั่งที่ปลอดภัย ถ้า query ช้า)
        long start = System.nanoTime();
        try {
            // สร้าง row ถ้ายังไม่มี (first boot ของทั้ง cluster)
            repository.insertIfNotExists(lockName, nodeId);

            int updated = repository.tryAcquireOrRenew(lockName, nodeId, LOCK_EXPIRE_SEC);
            leaderUntilNanos = updated > 0 ? start + LEASE_NANOS : 0;

            if (updated > 0) {
                log.debug("Scheduler lock held by this node ({})", nodeId);
            } else {
                log.debug("Scheduler lock held by another node — standing by ({})", nodeId);
            }
        } catch (Exception e) {
            // ไม่ reset leaderUntilNanos: lease เดิมยังใช้ได้จนหมดอายุ แล้ว isLeader() จะเป็น false เอง
            log.error("Error acquiring scheduler lock: {}", e.getMessage());
        }
        return isLeader();
    }

    @Transactional(noRollbackFor = Exception.class)
    public void releaseLock() {
        leaderUntilNanos = 0;
        try {
            // ทำให้ heartbeat หมดอายุทันที → node อื่น steal ได้ในรอบ heartbeat ถัดไป
            int released = repository.release(lockName, nodeId);
            log.info("Scheduler lock {} by node ({})", released > 0 ? "released" : "was not held", nodeId);
        } catch (Exception e) {
            log.error("Error releasing scheduler lock: {}", e.getMessage());
        }
    }
}
