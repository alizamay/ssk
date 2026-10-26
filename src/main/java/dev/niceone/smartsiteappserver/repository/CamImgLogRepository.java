package dev.niceone.smartsiteappserver.repository;

import dev.niceone.smartsiteappserver.model.AlarmLog;
import dev.niceone.smartsiteappserver.model.CamImgLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface CamImgLogRepository extends JpaRepository<CamImgLog, Long>, JpaSpecificationExecutor<CamImgLog> {
}
