package dev.niceone.smartsiteappserver.repository;

import dev.niceone.smartsiteappserver.model.OnsiteCheckLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OnsiteCheckLogRepository
        extends JpaRepository<OnsiteCheckLog, Long>, OnsiteCheckLogRepositoryCustom {

    // Retention: delete in bounded batches so one run never holds a long lock or a huge transaction
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM smartsite.onsite_check_log WHERE id IN (" +
                   "SELECT id FROM smartsite.onsite_check_log WHERE request_time < :cutoff LIMIT :batchSize)",
           nativeQuery = true)
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff, @Param("batchSize") int batchSize);
}
