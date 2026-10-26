package dev.niceone.smartsiteappserver.repository;

import dev.niceone.smartsiteappserver.model.CommonLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommonLogRepository extends JpaRepository<CommonLog, Long> {
}

