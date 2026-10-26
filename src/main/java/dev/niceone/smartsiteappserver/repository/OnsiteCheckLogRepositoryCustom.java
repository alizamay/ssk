package dev.niceone.smartsiteappserver.repository;

import dev.niceone.smartsiteappserver.model.OnsiteCheckLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

public interface OnsiteCheckLogRepositoryCustom {

    Page<OnsiteCheckLog> findByFilters(String siteCode, LocalDateTime startTime,
                                       LocalDateTime endTime, Integer responseStatus,
                                       Pageable pageable);

    List<OnsiteCheckLog> findAllByFilters(String siteCode, LocalDateTime startTime,
                                          LocalDateTime endTime, Integer responseStatus);
}
