package dev.niceone.smartsiteappserver.service;

import dev.niceone.smartsiteappserver.model.LogEntry;
import dev.niceone.smartsiteappserver.model.SiteEventLog;
import dev.niceone.smartsiteappserver.model.SiteEventResolveHistory;
import dev.niceone.smartsiteappserver.model.SiteEventStatSummary;
import dev.niceone.smartsiteappserver.payload.request.LogUpdateAlarm;
import dev.niceone.smartsiteappserver.repository.*;
import dev.niceone.smartsiteappserver.specification.SiteEventLogSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SiteEventLogService {

    private final SiteEventLogRepository logRepo;
    private final SiteEventResolveHistoryRepository resolveRepo;
    private final SiteEventStatSummaryRepository statSummaryRepo;
    private final SiteEventResolveHistoryRepositoryImpl resolveRepoImp;
    private final SiteEventLogCustomRepositoryImpl eventImp;
    @Transactional
    public SiteEventLog addEventLog(LogEntry logEntry) {

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = todayStart.plusDays(1);
        LocalDate todayDate = LocalDate.now();

        Optional<SiteEventLog> optionalLog = logRepo.findFirstBySiteIdAndAlarmTypeAndCabinetIdAndCreateDateBetween(
                logEntry.getSite_id(), logEntry.getAlarm_type(), logEntry.getCabinet_id(), todayStart, todayEnd);

        LocalDateTime now = LocalDateTime.now();

        SiteEventLog savedLog;

        if (!optionalLog.isPresent()) {
            // สร้าง log ใหม่
            SiteEventLog newLog = new SiteEventLog();
            newLog.setId(UUID.randomUUID());
            newLog.setActive(logEntry.isActive());                      // ตัวอย่างตั้งค่า default
            newLog.setAlarmType(logEntry.getAlarm_type());
            newLog.setCabinetId(logEntry.getCabinet_id());
            newLog.setDeviceId(logEntry.getDevice_id());                    // ถ้าไม่มีข้อมูล
            newLog.setResolveDate(logEntry.getResolve_date());                 // ยังไม่ resolve
            newLog.setSiteId(logEntry.getSite_id());
            newLog.setSiteCode(logEntry.getSite_code());
            newLog.setSiteNameTh(logEntry.getSite_name_th());
            newLog.setSiteNameEn(logEntry.getSite_name_en());
            newLog.setCreateDate(now);
            newLog.setUpdateDate(now);
            newLog.setWriteToFile(logEntry.isWrite_to_file());                 // ตัวอย่างตั้ง default
            newLog.setAlarmTypeCode(logEntry.getAlarm_type_code());
            newLog.setAlarmTypeName(logEntry.getAlarm_type_name());
            newLog.setCabinetNo(logEntry.getCabinet_no());
            newLog.setCabinetCode(logEntry.getCabinet_code());
            newLog.setLockEventId(logEntry.getLock_event_id());
            newLog.setLockEventName(logEntry.getLock_event_name());
            newLog.setDoorEventId(logEntry.getDoor_event_id());
            newLog.setDoorEventName(logEntry.getDoor_event_name());
            newLog.setStatusCode(logEntry.getStatus_code());
            newLog.setRegionCode(logEntry.getRegion_code());
            newLog.setRegionName(logEntry.getRegion_name());
            newLog.setIsResolve(false);
            savedLog = logRepo.save(newLog);


        } else {
            SiteEventLog existingLog = optionalLog.get();

            existingLog.setUpdateDate(now);
            existingLog.setIsResolve(false);
            savedLog = logRepo.save(existingLog);
        }

        // --- อัปเดต site_event_stat_summary ---
        // หา record summary สำหรับ logId + วันนี้
        LocalDate summaryDate = todayDate;
        Optional<SiteEventStatSummary> optionalStat = statSummaryRepo.findByLogIdAndDate(savedLog.getId(), summaryDate);

        if (!optionalStat.isPresent()) {
            // สร้างใหม่
            SiteEventStatSummary newStat = SiteEventStatSummary.builder()
                    .logId(savedLog.getId())
                    .siteId(savedLog.getSiteId())
                    .siteCode(savedLog.getSiteCode())
                    .cabinetId(savedLog.getCabinetId())
                    .cabinetNo(savedLog.getCabinetNo())
                    .alarmType(savedLog.getAlarmType())
                    .alarmTypeName(savedLog.getAlarmTypeName())
                    .date(summaryDate)
                    .countCreateEvent(1)
                    .countResolveEvent(0)
                    .build();

            statSummaryRepo.save(newStat);
        } else {
            SiteEventStatSummary existingStat = optionalStat.get();
            existingStat.setCountCreateEvent(existingStat.getCountCreateEvent() + 1);
            statSummaryRepo.save(existingStat);
        }

        return savedLog;
    }

    @Transactional
    public void resolveEventUnCabinet(LogUpdateAlarm logUpdateAlarm) {
        // 1. Load SiteEventLog
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = todayStart.plusDays(1);
        LocalDate todayDate = LocalDate.now();
        Optional<SiteEventLog> optionalLog = logRepo.findFirstBySiteCodeAndAlarmTypeAndCreateDateBetween(
                logUpdateAlarm.getSite_code(), logUpdateAlarm.getAlarm_type(), todayStart, todayEnd);

        if (optionalLog.isPresent()) {
            SiteEventLog existingLog = optionalLog.get();

            existingLog.setResolveDate(LocalDateTime.now());
            existingLog.setIsResolve(true);
            logRepo.save(existingLog);

            SiteEventResolveHistory history = new SiteEventResolveHistory();
            Long nextId = resolveRepoImp.getNextId();
            history.setId(nextId);
            history.setLogId(existingLog.getId());
            history.setSiteId(existingLog.getSiteId());
            history.setSiteCode(existingLog.getSiteCode());
            history.setCabinetId(existingLog.getCabinetId());
            history.setCabinetNo(existingLog.getCabinetNo());
            history.setAlarmType(existingLog.getAlarmType());
            history.setAlarmTypeName(existingLog.getAlarmTypeName());
            history.setCreateDate(existingLog.getUpdateDate());
            history.setResolveDate(existingLog.getResolveDate());

            try {
                LocalDate PartitionDate = history.getCreateDate().toLocalDate();
                resolveRepoImp.ensurePartitionFor(PartitionDate);
                resolveRepo.save(history);
            } catch (Exception e) {
                e.printStackTrace();
            }


            LocalDate summaryDate = todayDate;
            Optional<SiteEventStatSummary> optionalStat = statSummaryRepo.findByLogIdAndDate(existingLog.getId(), summaryDate);

            if (optionalStat.isPresent()) {
                SiteEventStatSummary existingStat = optionalStat.get();
                existingStat.setCountResolveEvent(existingStat.getCountResolveEvent() + 1);
                statSummaryRepo.save(existingStat);
            }
        }
    }


    @Transactional
    public void resolveEventOnCabinet(LogEntry logEntry) {
        // 1. Load SiteEventLog
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = todayStart.plusDays(1);
        LocalDate todayDate = LocalDate.now();
        Optional<SiteEventLog> optionalLog = logRepo.findFirstBySiteIdAndAlarmTypeAndCabinetIdAndCreateDateBetween(
                logEntry.getSite_id(), logEntry.getAlarm_type(), logEntry.getCabinet_id(), todayStart, todayEnd);

        if (optionalLog.isPresent()) {
            SiteEventLog existingLog = optionalLog.get();

            existingLog.setResolveDate(LocalDateTime.now());
            existingLog.setIsResolve(true);
            logRepo.save(existingLog);

            SiteEventResolveHistory history = new SiteEventResolveHistory();
            Long nextId = resolveRepoImp.getNextId();
            history.setId(nextId);
            history.setLogId(existingLog.getId());
            history.setSiteId(existingLog.getSiteId());
            history.setSiteCode(existingLog.getSiteCode());
            history.setCabinetId(existingLog.getCabinetId());
            history.setCabinetNo(existingLog.getCabinetNo());
            history.setAlarmType(existingLog.getAlarmType());
            history.setAlarmTypeName(existingLog.getAlarmTypeName());
            history.setCreateDate(existingLog.getUpdateDate());
            history.setResolveDate(existingLog.getResolveDate());

            try {
                LocalDate PartitionDate = history.getCreateDate().toLocalDate();
                resolveRepoImp.ensurePartitionFor(PartitionDate);
                resolveRepo.save(history);
            } catch (Exception e) {
                e.printStackTrace();
            }


            LocalDate summaryDate = todayDate;
            Optional<SiteEventStatSummary> optionalStat = statSummaryRepo.findByLogIdAndDate(existingLog.getId(), summaryDate);

            if (optionalStat.isPresent()) {
                SiteEventStatSummary existingStat = optionalStat.get();
                existingStat.setCountResolveEvent(existingStat.getCountResolveEvent() + 1);
                statSummaryRepo.save(existingStat);
            }
        }
    }

    public Page<SiteEventLog> getFilteredUnresolvedLogs(
            String keyword,
            LocalDateTime start,
            LocalDateTime end,
            int page,
            int size
    ) {
        Specification<SiteEventLog> spec =
                SiteEventLogSpecification.unresolvedWithKeywordAndDate(keyword, start, end);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createDate"));

        return logRepo.findAll(spec, pageable);
    }


    public Page<SiteEventLog> getFilteredResolvedLogs(
            List<Integer> alarmTypes,
            String keyword,
            LocalDateTime start,
            LocalDateTime end,
            int page,
            int size
    ) {

        Page<SiteEventLog> ret = eventImp.findResolvedLogsWithFilters(keyword,alarmTypes,start,end,page,size);

        return ret;
    }

    public List<SiteEventLog> findTodaysLogs(Boolean isResolve, Integer alarmType, String siteId) {
        // กำหนดช่วงเวลาของ "วันนี้"
        LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MIN); // วันนี้ เวลา 00:00:00
        LocalDateTime endOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);   // วันนี้ เวลา 23:59:59.999...

        // เรียกใช้งานเมธอดที่สร้างไว้ใน Repository
        return logRepo.findByIsResolveAndAlarmTypeAndSiteIdAndCreateDateBetween(
                isResolve,
                alarmType,
                siteId,
                startOfDay,
                endOfDay
        );
    }
}
