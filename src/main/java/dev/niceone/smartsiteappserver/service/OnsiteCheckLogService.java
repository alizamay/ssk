package dev.niceone.smartsiteappserver.service;

import dev.niceone.smartsiteappserver.model.OnsiteCheckLog;
import dev.niceone.smartsiteappserver.repository.OnsiteCheckLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class OnsiteCheckLogService {

    @Autowired
    private OnsiteCheckLogRepository repository;

    @Async
    public void save(String siteCode, String clientIp, int responseStatus, String responseRaw) {
        try {
            OnsiteCheckLog entry = new OnsiteCheckLog();
            entry.setSiteCode(siteCode);
            entry.setClientIp(clientIp);
            entry.setRequestTime(LocalDateTime.now());
            entry.setResponseStatus(responseStatus);
            entry.setResponseRaw(responseRaw);
            repository.save(entry);
        } catch (Exception e) {
            log.error("Failed to save OnsiteCheckLog: {}", e.getMessage());
        }
    }

    public Page<OnsiteCheckLog> findByFilters(String siteCode, LocalDateTime startTime,
                                               LocalDateTime endTime, Integer responseStatus,
                                               Pageable pageable) {
        return repository.findByFilters(siteCode, startTime, endTime, responseStatus, pageable);
    }

    public List<OnsiteCheckLog> findAllByFilters(String siteCode, LocalDateTime startTime,
                                                  LocalDateTime endTime, Integer responseStatus) {
        return repository.findAllByFilters(siteCode, startTime, endTime, responseStatus);
    }
}
