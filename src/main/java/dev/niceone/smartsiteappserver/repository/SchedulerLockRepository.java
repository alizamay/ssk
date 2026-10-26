package dev.niceone.smartsiteappserver.repository;

import dev.niceone.smartsiteappserver.model.SchedulerLock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SchedulerLockRepository extends JpaRepository<SchedulerLock, String> {

    // สร้าง row แรกถ้ายังไม่มี (first boot)
    @Modifying
    @Query(value = "INSERT INTO smartsite.scheduler_lock (lock_name, node_id, acquired_at, heartbeat_at) " +
                   "VALUES (:lockName, :nodeId, NOW(), NOW()) " +
                   "ON CONFLICT (lock_name) DO NOTHING",
           nativeQuery = true)
    int insertIfNotExists(@Param("lockName") String lockName, @Param("nodeId") String nodeId);

    // ยึด lock: สำเร็จถ้าเราถือ lock อยู่แล้ว หรือ lock ของคนอื่น expired
    // ใช้ CAST(...AS INTERVAL) แทน ::interval เพราะ JPA ตีความ :: เป็น named param
    @Modifying
    @Query(value = "UPDATE smartsite.scheduler_lock " +
                   "SET node_id = :nodeId, " +
                   "    acquired_at = CASE WHEN node_id = :nodeId THEN acquired_at ELSE NOW() END, " +
                   "    heartbeat_at = NOW() " +
                   "WHERE lock_name = :lockName " +
                   "AND (node_id = :nodeId OR heartbeat_at < NOW() - CAST((:expireSec || ' seconds') AS INTERVAL))",
           nativeQuery = true)
    int tryAcquireOrRenew(@Param("lockName") String lockName,
                          @Param("nodeId") String nodeId,
                          @Param("expireSec") int expireSec);

    // เช็คว่า node นี้ถือ lock อยู่และ heartbeat ยังสด
    @Query(value = "SELECT COUNT(*) FROM smartsite.scheduler_lock " +
                   "WHERE lock_name = :lockName AND node_id = :nodeId " +
                   "AND heartbeat_at >= NOW() - CAST((:expireSec || ' seconds') AS INTERVAL)",
           nativeQuery = true)
    int countActiveLock(@Param("lockName") String lockName,
                        @Param("nodeId") String nodeId,
                        @Param("expireSec") int expireSec);
}
