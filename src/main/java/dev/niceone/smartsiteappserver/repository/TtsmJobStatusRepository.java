package dev.niceone.smartsiteappserver.repository;

import dev.niceone.smartsiteappserver.model.TtsmJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TtsmJobStatusRepository extends JpaRepository<TtsmJobStatus, Long> {

    Optional<TtsmJobStatus> findFirstBySiteCodeOrderByReceivedAtDesc(String siteCode);

    /**
     * เช็คว่ามี jobId ของ siteCode นี้ที่ latest status ยังเป็น "On-Site" อยู่หรือไม่
     * (ยังไม่ถูก update เป็น Done/Held)
     */
    @Query("SELECT COUNT(t) > 0 FROM TtsmJobStatus t " +
           "WHERE t.siteCode = ?1 " +
           "AND t.id IN (SELECT MAX(t2.id) FROM TtsmJobStatus t2 WHERE t2.siteCode = ?1 GROUP BY t2.jobId) " +
           "AND t.jobStatus = 'On-Site'")
    boolean existsActiveOnsiteJob(String siteCode);

    List<TtsmJobStatus> findBySiteCodeOrderByReceivedAtDesc(String siteCode);

    Optional<TtsmJobStatus> findFirstByJobIdOrderByReceivedAtDesc(String jobId);

    @Query("SELECT t FROM TtsmJobStatus t WHERE t.siteCode = ?1 AND t.jobStatus = 'On-Site' ORDER BY t.receivedAt DESC")
    List<TtsmJobStatus> findOnsiteJobsBySiteCode(String siteCode);

    void deleteByReceivedAtBefore(LocalDateTime dateTime);

    /**
     * ดึง site ทั้งหมดที่มี jobId ที่ latest status ยังเป็น "On-Site" อยู่
     * (group by jobId เพื่อรองรับหลาย job ต่อ site)
     */
    @Query("SELECT t FROM TtsmJobStatus t WHERE t.id IN (" +
           "SELECT MAX(t2.id) FROM TtsmJobStatus t2 GROUP BY t2.siteCode, t2.jobId" +
           ") AND t.jobStatus = 'On-Site' ORDER BY t.receivedAt DESC")
    List<TtsmJobStatus> findAllLatestOnSite();

    /**
     * ดึงสถานะล่าสุดของทุก site (1 row ต่อ siteCode) พร้อม filter
     * ใช้ MAX(id) GROUP BY siteCode เพื่อแสดงสถานะปัจจุบันของแต่ละ site
     */
    @Query("SELECT t FROM TtsmJobStatus t WHERE t.id IN (" +
           "SELECT MAX(t2.id) FROM TtsmJobStatus t2 GROUP BY t2.siteCode" +
           ") AND (:jobStatus IS NULL OR t.jobStatus = :jobStatus) " +
           "AND (:keyword IS NULL OR t.siteCode LIKE %:keyword% OR t.jobId LIKE %:keyword%) " +
           "ORDER BY t.receivedAt DESC")
    Page<TtsmJobStatus> findAllLatestGrouped(
            @org.springframework.data.repository.query.Param("jobStatus") String jobStatus,
            @org.springframework.data.repository.query.Param("keyword") String keyword,
            Pageable pageable);

}
