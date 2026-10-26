package dev.niceone.smartsiteappserver.service;

import dev.niceone.smartsiteappserver.repository.SchedulerLockRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
public class SchedulerLockService {

    private static final String LOCK_NAME = "main_scheduler";
    // lock expire ถ้าไม่มี heartbeat นาน 120 วินาที (4 รอบ heartbeat)
    private static final int LOCK_EXPIRE_SEC = 120;

    // UUID unique ต่อ process — generate ครั้งเดียวตอน startup
    private final String nodeId = UUID.randomUUID().toString();

    private volatile boolean isLeader = false;

    @Autowired
    private SchedulerLockRepository repository;

    public String getNodeId() {
        return nodeId;
    }

    public boolean isLeader() {
        return isLeader;
    }

    /**
     * พยายามยึด lock หรือ renew ถ้าถือ lock อยู่แล้ว
     * เรียกทุก 30 วินาทีจาก SiteScheduler
     */
    @Transactional(noRollbackFor = Exception.class)
    public boolean tryAcquireOrRenew() {
        try {
            // สร้าง row ถ้ายังไม่มี (first boot ของทั้ง cluster)
            repository.insertIfNotExists(LOCK_NAME, nodeId);

            int updated = repository.tryAcquireOrRenew(LOCK_NAME, nodeId, LOCK_EXPIRE_SEC);
            isLeader = updated > 0;

            if (isLeader) {
                log.debug("Scheduler lock held by this node ({})", nodeId);
            } else {
                log.debug("Scheduler lock held by another node — standing by ({})", nodeId);
            }
        } catch (Exception e) {
            log.error("Error acquiring scheduler lock: {}", e.getMessage());
            isLeader = false;
        }
        return isLeader;
    }

    /**
     * ตรวจสอบจาก DB ว่า node นี้ยังถือ lock จริงไหม
     */
    @Transactional(readOnly = true)
    public boolean verifyLeadership() {
        try {
            return repository.countActiveLock(LOCK_NAME, nodeId, LOCK_EXPIRE_SEC) > 0;
        } catch (Exception e) {
            log.error("Error verifying scheduler lock: {}", e.getMessage());
            return false;
        }
    }

    @Transactional(noRollbackFor = Exception.class)
    public void releaseLock() {
        try {
            // reset heartbeat ให้เก่า → node อื่น steal ได้ทันที
            repository.tryAcquireOrRenew(LOCK_NAME, "__released__", LOCK_EXPIRE_SEC);
            isLeader = false;
            log.info("Scheduler lock released by node ({})", nodeId);
        } catch (Exception e) {
            log.error("Error releasing scheduler lock: {}", e.getMessage());
        }
    }
}
