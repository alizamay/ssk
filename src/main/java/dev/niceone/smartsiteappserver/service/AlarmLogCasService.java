package dev.niceone.smartsiteappserver.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import dev.niceone.smartsiteappserver.dto.AuthinLogDto;
import dev.niceone.smartsiteappserver.exception.AppException;
import dev.niceone.smartsiteappserver.mapper.AlarmLogModelMapper;
import dev.niceone.smartsiteappserver.model.*;
import dev.niceone.smartsiteappserver.model.TtsmJobStatus;
import dev.niceone.smartsiteappserver.model.constant.AlarmTypeCodeEnum;
import dev.niceone.smartsiteappserver.payload.AlarmLogResponse;
import dev.niceone.smartsiteappserver.payload.ApiResponse;
import dev.niceone.smartsiteappserver.payload.PagedResponse;
import dev.niceone.smartsiteappserver.payload.request.*;
import dev.niceone.smartsiteappserver.payload.response.*;
import dev.niceone.smartsiteappserver.repository.*;
import dev.niceone.smartsiteappserver.security.UserPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@EnableAsync
@Slf4j
@Service
public class AlarmLogCasService {

    private UserRepository userRepository;
    private SiteRepository siteRepository;
    private CabinetRepository cabinetRepository;
    private RegionRepository regionRepository;

    @Autowired
    private Environment env;

    @Autowired
    private AuthinService authinService;

    @Autowired
    private SiteEventLogService siteEventLogService;

    @Autowired
    private SiteEventStatSummaryRepository siteEventStatSummaryRepository;

    @Autowired
    private SiteEventLogCustomRepositoryImpl siteEventLogCustomRepositoryImpl;

    @Autowired
    private SiteEventResolveHistoryRepository siteEventResolveHistoryRepository;

    @Autowired
    private TtsmWebhookService ttsmWebhookService;

    @Autowired
    private AlarmNotifyGate alarmNotifyGate;

    @Autowired
    public AlarmLogCasService(UserRepository userRepository,SiteRepository siteRepository,CabinetRepository cabinetRepository,RegionRepository regionRepository) {
        this.userRepository = userRepository;
        this.siteRepository = siteRepository;
        this.cabinetRepository = cabinetRepository;
        this.regionRepository = regionRepository;
    }



    AlarmTypeCodeEnum[] reportTypes = {
            AlarmTypeCodeEnum.ALARM_TYPE_VIBRAT_EXCESS,
            AlarmTypeCodeEnum.ALARM_TYPE_UNAUTH_OPENED,
            AlarmTypeCodeEnum.ALARM_TYPE_MOTION_EXCESS,
            AlarmTypeCodeEnum.ALARM_TYPE_COMM_LOST_SERV,
            AlarmTypeCodeEnum.ALARM_TYPE_COMM_LOST_PI,
            AlarmTypeCodeEnum.ALARM_TYPE_SLAVE_DOWN
    };
    // Raises and clears of one site and alarm type run one after another, in call order. They
    // used to run concurrently, so a clear could finish first and then be overwritten by the
    // raise before it: site_event_log stayed unresolved and Centralized Fault was never cleared.
    private final ConcurrentHashMap<String, CompletableFuture<?>> alarmQueues = new ConcurrentHashMap<>();

    public void getOrCreateAlarmLog(AlarmTypeCodeEnum alarmType, String siteCode, Cabinet cabinet, Device device, LocalDateTime resolveDate) throws JsonProcessingException {
        // Taken in the caller's order; the work below finishes later on another thread
        LocalDateTime eventAt = resolveDate != null ? resolveDate : LocalDateTime.now();
        String queueKey = siteCode + ":" + alarmType.getId();

        Supplier<LogEntry> work = () -> {
            LogEntry alarmLog = null;
            Site site = siteRepository.findByCodeWithRegion(siteCode).orElse(null);

            if(resolveDate == null){
                LogEntry newLog = new LogEntry();
                newLog.setAlarm_type(alarmType.getId());
                newLog.setSite_id(String.valueOf(site.getId()));
                if (cabinet != null) {
                    newLog.setCabinet_id(String.valueOf(cabinet.getId()));
                }
                if (device != null) {
                    newLog.setDevice_id(String.valueOf(device.getId()));
                }
                newLog.setCreate_date(LocalDateTime.now());
                newLog.setWrite_to_file(false);
                newLog.setActive(true);

                // Additional fields
                newLog.setAlarm_type_code(alarmType.code);
                newLog.setAlarm_type_name(alarmType.name);

                if (cabinet != null) {
                    newLog.setCabinet_no(String.valueOf(cabinet.getCabinetNo()));
                    newLog.setCabinet_code(cabinet.getCode());
                    newLog.setLock_event_id(cabinet.getLockEventCode().id);
                    newLog.setLock_event_name(cabinet.getLockEventCode().name);
                    newLog.setDoor_event_id(cabinet.getDoorEventCode().id);
                    newLog.setDoor_event_name(cabinet.getDoorEventCode().name);
                }

                newLog.setSite_code(siteCode);
                newLog.setSite_name_th(site.getName_th());
                newLog.setSite_name_en(site.getName_en());
                newLog.setStatus_code(site.getStatus().code);
                newLog.setRegion_code(site.getRegion().getCode());
                newLog.setRegion_name(site.getRegion().getName());

                try {
                    //addLog(newLog);
                    siteEventLogService.addEventLog(newLog);


                    if (Arrays.asList(reportTypes).contains(AlarmTypeCodeEnum.getById(alarmType.getId()))){
                        // Check throttling before sending to prevent alarm flooding
                        boolean isClear = (newLog.getResolve_date() != null);
                        boolean shouldSend = alarmNotifyGate.shouldSend(
                            newLog.getSite_code(),
                            alarmType.getId(),
                            isClear,
                            eventAt
                        );

                        if (shouldSend) {
                            String msgAlarm = tcpAlarmPayload(newLog);
                            AuthinLogDto aDto = new AuthinLogDto();
                            aDto.setSiteCode(newLog.getSite_code());
                            aDto.setIsClear(isClear);
                            sendTcpAlarm(msgAlarm, aDto);
                        } else {
                            log.debug("Alarm throttled for site {} alarm type {} (flapping detected)",
                                newLog.getSite_code(), alarmType.getId());
                        }
                    }

                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }else{

                LogUpdateAlarm updateLog = new LogUpdateAlarm();
                updateLog.setAlarm_type(alarmType.getId());
                updateLog.setResolve_date(resolveDate);
                updateLog.setSite_code(siteCode);
                try {
                    //updateLogResolve(updateLog);
                    siteEventLogService.resolveEventUnCabinet(updateLog);
                    if (Arrays.asList(reportTypes).contains(AlarmTypeCodeEnum.getById(alarmType.getId()))){
                        // Check throttling before sending to prevent alarm flooding
                        boolean isClear = (updateLog.getResolve_date() != null);
                        boolean shouldSend = alarmNotifyGate.shouldSend(
                            updateLog.getSite_code(),
                            alarmType.getId(),
                            isClear,
                            eventAt
                        );

                        if (shouldSend) {
                            String msgAlarm = tcpAlarmPayloadUpdate(updateLog);
                            AuthinLogDto aDto = new AuthinLogDto();
                            aDto.setSiteCode(updateLog.getSite_code());
                            aDto.setIsClear(isClear);
                            sendTcpAlarm(msgAlarm, aDto);
                        } else {
                            log.debug("Alarm throttled for site {} alarm type {} (flapping detected)",
                                updateLog.getSite_code(), alarmType.getId());
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }


            return alarmLog;
        };

        CompletableFuture<?> next = alarmQueues.compute(queueKey, (k, prev) ->
                (prev == null ? CompletableFuture.completedFuture(null) : prev)
                        .handleAsync((r, ex) -> work.get()));
        next.whenComplete((r, ex) -> {
            if (ex != null) {
                log.warn("alarm event {} failed: {}", queueKey, ex.getMessage());
            }
            alarmQueues.remove(queueKey, next);
        });
    }


    public List<SiteEventLog> getAllByAlarmType(AlarmTypeCodeEnum alarmType, Boolean isResolve,Site site) {

        LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MIN); // วันนี้ เวลา 00:00:00
        LocalDateTime endOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);   // วันนี้ เวลา 23:59:59.999...

        // เรียกใช้งานเมธอดที่สร้างไว้ใน Repository
        return siteEventLogService.findTodaysLogs(
                isResolve,
                alarmType.getId(),
                site.getId().toString()
        );
    }

    public PagedResponse<AlarmLogResponse> getEventList(Specification<AlarmLog> spec, int page, int size,
                                                        UserPrincipal currentUser,String startDate,String endDate,String keyword) {
        // User
        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new AppException("User not set."));


//        FetchLogsResponse fetchLogs = fetchLogs(null,null,null,null,size,"");
//        List<AlarmLogResponse> alarmLogResponses = fetchLogs.getLogs().stream().map(AlarmLogModelMapper::mapLogEntryResponse).collect(Collectors.toList());

//        List<LogEntry> retLog = fetchAllLogs().stream()
//                .filter(person -> !person.getCreate_date().isBefore(startDate) && !person.getCreate_date().isAfter(endDate))
//                .collect(Collectors.toList());

        LocalDateTime startDateTime = LocalDateTime.now().with(LocalTime.of(0, 0, 1));
        LocalDateTime endDateTime = LocalDateTime.now().with(LocalTime.of(23, 59, 59));

        String alarm_type = "";
        String site_code = "";
        String region_code = "";
        String cabinet_code = "";

        if( startDate != null && !startDate.isEmpty() && endDate != null && !endDate.isEmpty()){
            try {
                // Parse the string to LocalDateTime
                startDateTime = (LocalDateTime.parse(startDate)).plusHours(7);
                endDateTime = (LocalDateTime.parse(endDate)).plusHours(7);

            }catch (Exception ex){

            }
        }

        if(keyword != null && !keyword.isEmpty()){
            alarm_type = keyword;
            site_code = keyword;
            region_code = keyword;
            cabinet_code = keyword;
        }
        String startDateTimeStr = startDateTime.format(DateTimeFormatter.ISO_DATE_TIME);
        String endDateTimeStr = endDateTime.format(DateTimeFormatter.ISO_DATE_TIME);


        Page<SiteEventLog> siteLog = siteEventLogService.getFilteredUnresolvedLogs(keyword,startDateTime,endDateTime,page,size);
        //FetchLogsResponse retLog = fetchResolveDateIsNullAllLogs(startDateTimeStr,endDateTimeStr,alarm_type,site_code,region_code,cabinet_code,size,(page + 1));

        //retLog.sort(Comparator.comparing(LogEntry::getCreate_date).reversed());


        List<AlarmLogResponse> alarmLogResponses = siteLog.getContent().stream().map(AlarmLogModelMapper::mapToAlarmLogResponse).collect(Collectors.toList());

        // Batch load SiteEventStatSummary to avoid N+1 queries
        List<UUID> logIds = alarmLogResponses.stream()
                .map(AlarmLogResponse::getLogId)
                .collect(Collectors.toList());

        Map<UUID, SiteEventStatSummary> summaryMap = new HashMap<>();
        if (!logIds.isEmpty()) {
            List<SiteEventStatSummary> summaries = siteEventStatSummaryRepository.findByLogIdIn(logIds);
            summaryMap = summaries.stream()
                    .collect(Collectors.toMap(SiteEventStatSummary::getLogId, summary -> summary));
        }

        for (AlarmLogResponse res : alarmLogResponses) {
            UUID logId = res.getLogId();
            Integer cnt = 0;
            SiteEventStatSummary summaryOpt = summaryMap.get(logId);
            if(summaryOpt != null){
                cnt = summaryOpt.getCountCreateEvent();
            }
            res.setCount(cnt);
        }

        int totalPages = siteLog.getTotalPages();
        return new PagedResponse<>(alarmLogResponses, page,
                size, siteLog.getContent().size(), totalPages, page == totalPages);

//        return new PagedResponse<>(alarmLogResponses, fetchLogs.getCurrentPage(),
//                size, fetchLogs.getTotalCount(), fetchLogs.getTotalPages(), fetchLogs.getCurrentPage() == fetchLogs.getTotalPages());
    }

    public PagedResponse<AlarmLogResponse> getAlarmList(Specification<AlarmLog> spec, int page, int size,
                                                        UserPrincipal currentUser,String startDate,String endDate,String keyword) {
        // User
        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new AppException("User not set."));


        LocalDateTime startDateTime = LocalDateTime.now().with(LocalTime.of(0, 0, 1));
        LocalDateTime endDateTime = LocalDateTime.now().with(LocalTime.of(23, 59, 59));

        if(startDate != null && !startDate.isEmpty() && endDate != null && !endDate.isEmpty()){
            try {
                // Parse the string to LocalDateTime
                startDateTime = (LocalDateTime.parse(startDate)).plusHours(7);
                endDateTime = (LocalDateTime.parse(endDate)).plusHours(7);

            }catch (Exception ex){

            }
        }


        String siteCode = "";
        String regionCode = "";
        String cabinetCode = "";
        String alarm_type_name = "";

        if(keyword != null && !keyword.isEmpty()){
             siteCode = keyword;
             regionCode = keyword;
             cabinetCode = keyword;
            alarm_type_name = keyword;
        }
        List<Integer> alarmTypes = Arrays.asList(15, 16, 17, 18);

        Page<SiteEventLog> siteLog = siteEventLogService.getFilteredResolvedLogs(alarmTypes,keyword,startDateTime,endDateTime,page,size);

        List<AlarmLogResponse> alarmLogResponses = siteLog.getContent().stream().map(AlarmLogModelMapper::mapToAlarmLogResponse).collect(Collectors.toList());

        // Batch load SiteEventStatSummary to avoid N+1 queries
        List<UUID> logIds = alarmLogResponses.stream()
                .map(AlarmLogResponse::getLogId)
                .collect(Collectors.toList());

        Map<UUID, SiteEventStatSummary> summaryMap = new HashMap<>();
        if (!logIds.isEmpty()) {
            List<SiteEventStatSummary> summaries = siteEventStatSummaryRepository.findByLogIdIn(logIds);
            summaryMap = summaries.stream()
                    .collect(Collectors.toMap(SiteEventStatSummary::getLogId, summary -> summary));
        }

        for (AlarmLogResponse res : alarmLogResponses) {
            UUID logId = res.getLogId();
            Integer cnt = 0;
            Integer cnt_resolve = 0;
            SiteEventStatSummary summaryOpt = summaryMap.get(logId);
            if(summaryOpt != null){
                cnt = summaryOpt.getCountCreateEvent();
                cnt_resolve = summaryOpt.getCountResolveEvent();
            }
            res.setCount(cnt);
            res.setResolve_count(cnt_resolve);
        }

        List<AlarmLogResponse> newListLogs = new ArrayList<>();

        // Batch load SiteEventResolveHistory to avoid N+1 queries
        Map<UUID, List<SiteEventResolveHistory>> historyMap = new HashMap<>();
        if (!logIds.isEmpty()) {
            List<SiteEventResolveHistory> allHistories = siteEventResolveHistoryRepository.findByLogIdIn(logIds);
            historyMap = allHistories.stream()
                    .collect(Collectors.groupingBy(SiteEventResolveHistory::getLogId));
        }

        for (AlarmLogResponse original : alarmLogResponses) {
            try {
                List<SiteEventResolveHistory> historyResolve = historyMap.get(original.getLogId());

                if(historyResolve != null){

                    for(SiteEventResolveHistory history : historyResolve){

                        AlarmLogResponse copy = new AlarmLogResponse();
                        copy.setId(original.getId());
                        copy.setCreatedAt(history.getCreateDate());
                        copy.setResolveDate(history.getResolveDate()); // แก้เฉพาะ resolveDate
                        copy.setActive(original.getActive());

                        copy.setAlarmTypeCode(original.getAlarmTypeCode());
                        copy.setAlarmTypeName(original.getAlarmTypeName());

                        copy.setDeviceTypeCode(original.getDeviceTypeCode());
                        copy.setDeviceTypeName(original.getDeviceTypeName());
                        copy.setStatusCode(original.getStatusCode());
                        copy.setStatusName(original.getStatusName());
                        copy.setEventTypeCodeEnum(original.getEventTypeCodeEnum());

                        copy.setCabinetNo(original.getCabinetNo());
                        copy.setCabinetCode(original.getCabinetCode());
                        copy.setLockEventCodeEnum(original.getLockEventCodeEnum());
                        copy.setLockEventName(original.getLockEventName());
                        copy.setLockEventTypeCodeEnum(original.getLockEventTypeCodeEnum());
                        copy.setDoorEventCodeEnum(original.getDoorEventCodeEnum());
                        copy.setDoorEventName(original.getDoorEventName());
                        copy.setDoorEventTypeCodeEnum(original.getDoorEventTypeCodeEnum());

                        copy.setSiteCode(original.getSiteCode());
                        copy.setSiteNameTh(original.getSiteNameTh());
                        copy.setSiteNameEn(original.getSiteNameEn());

                        copy.setRegionCode(original.getRegionCode());
                        copy.setRegionName(original.getRegionName());

                        copy.setPageState(original.getPageState());

                        copy.setCount(original.getCount());
                        copy.setResolve_count(original.getResolve_count());
                        copy.setTimestamps(original.getTimestamps());
                        copy.setLogId(original.getLogId());

                        newListLogs.add(copy);
                    }
                }

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        int totalPages = siteLog.getTotalPages();

        return new PagedResponse<>(newListLogs, page,
                size, siteLog.getSize(), totalPages, page == totalPages);

    }


    public PagedResponse<AlarmLogResponse> getActivityList(Specification<AlarmLog> spec, int page, int size,
                                                        UserPrincipal currentUser,String startDate,String endDate,String keyword) {
        // User
        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new AppException("User not set."));

        LocalDateTime startDateTime = LocalDateTime.now().with(LocalTime.of(0, 0, 1));
        LocalDateTime endDateTime = LocalDateTime.now().with(LocalTime.of(23, 59, 59));

        if(startDate != null && !startDate.isEmpty() && endDate != null && !endDate.isEmpty()){
            try {
                // Parse the string to LocalDateTime
                startDateTime = (LocalDateTime.parse(startDate)).plusHours(7);
                endDateTime = (LocalDateTime.parse(endDate)).plusHours(7);

            }catch (Exception ex){

            }
        }


        String siteCode = "";
        String regionCode = "";
        String cabinetCode = "";
        String alarm_type_name = "";

        if(keyword != null && !keyword.isEmpty()){
            siteCode = keyword;
            regionCode = keyword;
            cabinetCode = keyword;
            alarm_type_name = keyword;
        }

        List<Integer> alarmTypes = Arrays.asList(9,1,2);

        Page<SiteEventLog> siteLog = siteEventLogService.getFilteredResolvedLogs(alarmTypes,keyword,startDateTime,endDateTime,page,size);

        List<AlarmLogResponse> alarmLogResponses = siteLog.getContent().stream().map(AlarmLogModelMapper::mapToAlarmLogResponse).collect(Collectors.toList());

        // Batch load SiteEventStatSummary to avoid N+1 queries
        List<UUID> logIds = alarmLogResponses.stream()
                .map(AlarmLogResponse::getLogId)
                .collect(Collectors.toList());

        Map<UUID, SiteEventStatSummary> summaryMap = new HashMap<>();
        if (!logIds.isEmpty()) {
            List<SiteEventStatSummary> summaries = siteEventStatSummaryRepository.findByLogIdIn(logIds);
            summaryMap = summaries.stream()
                    .collect(Collectors.toMap(SiteEventStatSummary::getLogId, summary -> summary));
        }

        for (AlarmLogResponse res : alarmLogResponses) {
            UUID logId = res.getLogId();
            Integer cnt = 0;
            Integer cnt_resolve = 0;
            SiteEventStatSummary summaryOpt = summaryMap.get(logId);
            if(summaryOpt != null){
                cnt = summaryOpt.getCountCreateEvent();
                cnt_resolve = summaryOpt.getCountResolveEvent();
            }
            res.setCount(cnt);
            res.setResolve_count(cnt_resolve);
        }

        List<AlarmLogResponse> newListLogs = new ArrayList<>();

        // Batch load SiteEventResolveHistory to avoid N+1 queries
        Map<UUID, List<SiteEventResolveHistory>> historyMap = new HashMap<>();
        if (!logIds.isEmpty()) {
            List<SiteEventResolveHistory> allHistories = siteEventResolveHistoryRepository.findByLogIdIn(logIds);
            historyMap = allHistories.stream()
                    .collect(Collectors.groupingBy(SiteEventResolveHistory::getLogId));
        }

        for (AlarmLogResponse original : alarmLogResponses) {
            try {
                List<SiteEventResolveHistory> historyResolve = historyMap.get(original.getLogId());

                if(historyResolve != null){

                    for(SiteEventResolveHistory history : historyResolve){

                        AlarmLogResponse copy = new AlarmLogResponse();
                        copy.setId(original.getId());
                        copy.setCreatedAt(history.getCreateDate());
                        copy.setResolveDate(history.getResolveDate()); // แก้เฉพาะ resolveDate
                        copy.setActive(original.getActive());

                        copy.setAlarmTypeCode(original.getAlarmTypeCode());
                        copy.setAlarmTypeName(original.getAlarmTypeName());

                        copy.setDeviceTypeCode(original.getDeviceTypeCode());
                        copy.setDeviceTypeName(original.getDeviceTypeName());
                        copy.setStatusCode(original.getStatusCode());
                        copy.setStatusName(original.getStatusName());
                        copy.setEventTypeCodeEnum(original.getEventTypeCodeEnum());

                        copy.setCabinetNo(original.getCabinetNo());
                        copy.setCabinetCode(original.getCabinetCode());
                        copy.setLockEventCodeEnum(original.getLockEventCodeEnum());
                        copy.setLockEventName(original.getLockEventName());
                        copy.setLockEventTypeCodeEnum(original.getLockEventTypeCodeEnum());
                        copy.setDoorEventCodeEnum(original.getDoorEventCodeEnum());
                        copy.setDoorEventName(original.getDoorEventName());
                        copy.setDoorEventTypeCodeEnum(original.getDoorEventTypeCodeEnum());

                        copy.setSiteCode(original.getSiteCode());
                        copy.setSiteNameTh(original.getSiteNameTh());
                        copy.setSiteNameEn(original.getSiteNameEn());

                        copy.setRegionCode(original.getRegionCode());
                        copy.setRegionName(original.getRegionName());

                        copy.setPageState(original.getPageState());

                        copy.setCount(original.getCount());
                        copy.setResolve_count(original.getResolve_count());
                        copy.setTimestamps(original.getTimestamps());
                        copy.setLogId(original.getLogId());

                        newListLogs.add(copy);
                    }
                }

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        int totalPages = siteLog.getTotalPages();

        return new PagedResponse<>(newListLogs, page,
                size, siteLog.getSize(), totalPages, page == totalPages);
    }

    public ResponseEntity<ApiResponse> piAlarm(PiAlarmLogRequest alarmLogRequest) throws JsonProcessingException {
        LogEntry alarmLog = null;
        if (alarmLogRequest.getSite_code() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse(false, "site_code require"));
        }
        if (alarmLogRequest.getCabinet_code() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse(false, "cabinet_code require"));
        }

        Site site = siteRepository.findByCode(alarmLogRequest.getSite_code()).orElse(null);
//        AlarmTypeCodeEnum alarmType = AlarmTypeCodeEnum.getById(alarmLogRequest.getAlarm_id());
//        List<AlarmLog> siteAlarms = alarmLogRepository.findBySiteAndAlarmTypeAndResolveDateIsNull(site, alarmType);
//        alarmLog = Iterables.getLast(siteAlarms, null);
        Cabinet cabinet = cabinetRepository.findByCode(alarmLogRequest.getCabinet_code());
        if (cabinet == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse(false, "not found cabinet"));
        }
        AlarmTypeCodeEnum alarmType = AlarmTypeCodeEnum.getById(alarmLogRequest.getAlarm_id());
//        List<LogEntry> cabinetAlarms = findByCabinetAndAlarmTypeAndResolveDateIsNull(String.valueOf(cabinet.getId()), alarmType.getId());
//        alarmLog = Iterables.getLast(cabinetAlarms, null);

        // case resolve
        if (alarmLogRequest.getStatus() == 0) {
            log.info("piAlarm update resolve date site " + alarmLogRequest.getSite_code());
            // Found un-resolve alarm
            //updateResolveDateForSingleLog(alarmLog.getId().toString(),LocalDateTime.now());

            if(alarmLogRequest.getAlarm_id() == 18){
                cabinet.setOnline(true);
                cabinetRepository.saveAndFlush(cabinet);
            }

            LogUpdateAlarm updateLog = new LogUpdateAlarm();
            updateLog.setAlarm_type(alarmType.getId());
            updateLog.setResolve_date(LocalDateTime.now());
            updateLog.setSite_code(site.getCode());
//            try {
//                updateLogResolve(updateLog);
//
//            } catch (JsonProcessingException e) {
//                throw new RuntimeException(e);
//            }

            alarmLog = new LogEntry();
            alarmLog.setUpdate_date(LocalDateTime.now());
            alarmLog.setSite_code(site.getCode());
            alarmLog.setAlarm_type(alarmType.getId());
            alarmLog.setResolve_date(LocalDateTime.now());
            if (site != null){
                alarmLog.setSite_id(String.valueOf(site.getId()));
            }
            if (cabinet != null){
                alarmLog.setCabinet_id(String.valueOf(cabinet.getId()));
                alarmLog.setCabinet_code(String.valueOf(cabinet.getCode()));
            }
            siteEventLogService.resolveEventOnCabinet(alarmLog);
            try {

                if (Arrays.asList(reportTypes).contains(AlarmTypeCodeEnum.getById(alarmType.getId()))){
                    // Check throttling before sending to prevent alarm flooding
                    boolean isClear = (alarmLog.getResolve_date() != null);
                    boolean shouldSend = alarmNotifyGate.shouldSend(
                        site.getCode(),
                        alarmType.getId(),
                        isClear,
                        LocalDateTime.now()
                    );

                    if (shouldSend) {
                        String msgAlarm = tcpAlarmPayload(alarmLog);
                        AuthinLogDto aDto = new AuthinLogDto();
                        aDto.setSiteCode(alarmLog.getSite_code());
                        aDto.setIsClear(isClear);
                        sendTcpAlarm(msgAlarm, aDto);
                    } else {
                        log.debug("Alarm throttled for site {} alarm type {} (flapping detected)",
                            site.getCode(), alarmType.getId());
                    }
                }


                if(site != null){
                    //sendLogSuccess("",String.valueOf(alarmLog.getSite_id()),alarmType.getId());
                }

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse(true, "UPDATE OK"));
        }

        // new alarm
        if (alarmLogRequest.getStatus() == 1) {
            log.info("piAlarm new log site " + alarmLogRequest.getSite_code());
            LogEntry newLog = new LogEntry();
            newLog.setAlarm_type(alarmLogRequest.getAlarm_id());
            newLog.setSite_id(String.valueOf(site.getId()));
            newLog.setCabinet_id(String.valueOf(cabinet.getId()));
            newLog.setCreate_date(LocalDateTime.now());
            newLog.setWrite_to_file(false);
            newLog.setActive(true);

            // Additional fields
            newLog.setAlarm_type_code(alarmType.code);
            newLog.setAlarm_type_name(alarmType.name);

            if(cabinet != null){
                newLog.setCabinet_no(String.valueOf(cabinet.getCabinetNo()));
                newLog.setCabinet_code(cabinet.getCode());
                newLog.setLock_event_id(cabinet.getLockEventCode().id);
                newLog.setLock_event_name(cabinet.getLockEventCode().name);
                newLog.setDoor_event_id(cabinet.getDoorEventCode().id);
                newLog.setDoor_event_name(cabinet.getDoorEventCode().name);
                if(alarmLogRequest.getAlarm_id() == 18){
                    cabinet.setOnline(false);
                    cabinetRepository.saveAndFlush(cabinet);
                }

            }

            newLog.setSite_code(site.getCode());
            newLog.setSite_name_th(site.getName_th());
            newLog.setSite_name_en(site.getName_en());
            newLog.setStatus_code(site.getStatus().code);

//            Region region = site.getRegion();
//            newLog.setRegion_code(region.getCode());
//            newLog.setRegion_name(region.getName());

            //addLog(newLog);
            siteEventLogService.addEventLog(newLog);
            log.info("piAlarm new log success site " + alarmLogRequest.getSite_code());
            log.info("Execute piAlarm response. "
                    + Thread.currentThread().getName());
//            if (site != null){
//                alarmLog.setSite_id(String.valueOf(site.getId()));
//            }
            try {

                if (Arrays.asList(reportTypes).contains(AlarmTypeCodeEnum.getById(alarmType.getId()))){
                    // Check throttling before sending to prevent alarm flooding
                    boolean isClear = (newLog.getResolve_date() != null);
                    boolean shouldSend = alarmNotifyGate.shouldSend(
                        site.getCode(),
                        alarmType.getId(),
                        isClear,
                        LocalDateTime.now()
                    );

                    if (shouldSend) {
                        String msgAlarm = tcpAlarmPayload(newLog);
                        AuthinLogDto aDto = new AuthinLogDto();
                        aDto.setSiteCode(newLog.getSite_code());
                        aDto.setIsClear(isClear);
                        sendTcpAlarm(msgAlarm, aDto);
                    } else {
                        log.debug("Alarm throttled for site {} alarm type {} (flapping detected)",
                            site.getCode(), alarmType.getId());
                    }
                }

                if(site != null){
                  //  sendLogSuccess("",String.valueOf(alarmLog.getSite_id()),alarmType.getId());
                }

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse(true, "CREATE OK"));
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse(false, "Bad request"));
    }

    public String tcpAlarmPayload(LogEntry alarmLog) {


        String my_ip = env.getProperty("app.my_ip");
        String alarm_postfix = env.getProperty("app.alarm_postfix");
        String localhost = "10.242.15.22";

        if (my_ip != null) {
            localhost = my_ip;
        }

        String hostname = "SMARTSITE";

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss");
        String formatDateTime = LocalDateTime.now().format(formatter);
        if (alarmLog.getUpdate_date() != null) {
            formatDateTime = alarmLog.getUpdate_date().format(formatter);
        }

        String severity = AlarmTypeCodeEnum.getById(alarmLog.getAlarm_type()).severity;
        if(alarmLog.getResolve_date() != null) {
            severity = "clear";
            formatDateTime = alarmLog.getResolve_date().format(formatter);
        }

       // Site site = siteRepository.findByCode(alarmLog.getSite_code()).get();
        Site site = siteRepository.findByCodeWithProvince(alarmLog.getSite_code()).get();

        String str = "SMARTSITE|" +
                localhost + "|" +
                hostname + "|" + "|" +
                "|" +
                 "|" +
                "|" +
                "SmartSite Master device |" +
                 "|" +
                "|" +
                 "|";

        try{

            str = "SMARTSITE|" +
                    localhost + "|" +
                    hostname + "|" +
                    alarmLog.getSite_code() + "|" +
                    site.getIp_address() + "|" +
                    AlarmTypeCodeEnum.getById(alarmLog.getAlarm_type()).aKey + "|" +
                    AlarmTypeCodeEnum.getById(alarmLog.getAlarm_type()).aName + "|" +
                    "SmartSite Master device " + site.getCode() + "|" +
                    severity + "|" +
                    AlarmTypeCodeEnum.getById(alarmLog.getAlarm_type()).name + "|" +
                    formatDateTime + "|" +
                    site.getProvince().getCode();

        } catch (NullPointerException ex) {
            String msg = ex.getMessage();
        }catch (Exception ex)
        {
            String msg = ex.getMessage();
        }



        return str;
    }

    public String tcpAlarmPayloadUpdate(LogUpdateAlarm alarmLog) {


        String my_ip = env.getProperty("app.my_ip");
        String alarm_postfix = env.getProperty("app.alarm_postfix");
        String localhost = "10.242.15.22";

        if (my_ip != null) {
            localhost = my_ip;
        }

        String hostname = "SMARTSITE";

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss");
        String formatDateTime = LocalDateTime.now().format(formatter);


        String severity = AlarmTypeCodeEnum.getById(alarmLog.getAlarm_type()).severity;
        if(alarmLog.getResolve_date() != null) {
            severity = "clear";
            formatDateTime = alarmLog.getResolve_date().format(formatter);
        }

        //Site site = siteRepository.findByCode(alarmLog.getSite_code()).get();
        Site site = siteRepository.findByCodeWithProvince(alarmLog.getSite_code()).get();

        String str = "SMARTSITE|" +
                localhost + "|" +
                hostname + "|" + "|" +
                "|" +
                "|" +
                "|" +
                "SmartSite Master device |" +
                "|" +
                "|" +
                "|";

        try{

            str = "SMARTSITE|" +
                    localhost + "|" +
                    hostname + "|" +
                    alarmLog.getSite_code() + "|" +
                    site.getIp_address() + "|" +
                    AlarmTypeCodeEnum.getById(alarmLog.getAlarm_type()).aKey + "|" +
                    AlarmTypeCodeEnum.getById(alarmLog.getAlarm_type()).aName + "|" +
                    "SmartSite Master device " + site.getCode() + "|" +
                    severity + "|" +
                    AlarmTypeCodeEnum.getById(alarmLog.getAlarm_type()).name + "|" +
                    formatDateTime + "|" +
                    site.getProvince().getCode();

        } catch (NullPointerException ex) {
            String msg = ex.getMessage();
        }catch (Exception ex)
        {
            String msg = ex.getMessage();
        }

        return str;
    }


    /** Sends the held clear of each alarm episode that has been quiet long enough (see AlarmNotifyGate). */
    public void sendDueClears() {
        for (AlarmNotifyGate.Episode episode : alarmNotifyGate.closeQuietEpisodes()) {
            try {
                LogUpdateAlarm clear = new LogUpdateAlarm();
                clear.setAlarm_type(episode.alarmType);
                clear.setSite_code(episode.siteCode);
                clear.setResolve_date(episode.lastEventAt);
                AuthinLogDto aDto = new AuthinLogDto();
                aDto.setSiteCode(episode.siteCode);
                aDto.setIsClear(true);
                sendTcpAlarm(tcpAlarmPayloadUpdate(clear), aDto);
            } catch (Exception e) {
                log.warn("sending held clear failed {}:{} - {}", episode.siteCode, episode.alarmType, e.getMessage());
            }
        }
    }

    @Async
    public void sendTcpAlarm(String alarm, AuthinLogDto authinLogDto) {
        log.debug("Execute method sendTcpAlarm. {}", Thread.currentThread().getName());
        String alarm_ip = env.getProperty("app.alarm_ip");
        String alarm_port = env.getProperty("app.alarm_port");

        log.debug("sendTcpAlarm {} {}", alarm_ip, alarm_port);
        log.info(alarm);
        authinLogDto.setUrl(alarm_ip+":" + alarm_port);
        SocketAddress sockAddr = new InetSocketAddress(alarm_ip, Integer.parseInt(alarm_port));
        // try-with-resources: the socket was leaked (open fd) whenever connect() failed
        try (Socket socket = new Socket()) {
            socket.connect(sockAddr, 5000);
            socket.setSoTimeout(5000);
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
            writer.print(alarm);
            writer.flush();
            authinLogDto.setMessage(alarm + ",[SUCCESS]");
        } catch (UnknownHostException ex) {
            log.error("sendTcpAlarm Server not found: " + ex.getMessage());
            authinLogDto.setMessage(alarm + ",[FAIL:sendTcpAlarm Server not found "+ex.getMessage()+"]");
        } catch (IOException e) {
            log.error("sendTcpAlarm I/O error: " + e.getMessage());
            authinLogDto.setMessage(alarm + ",[FAIL:sendTcpAlarm I/O error "+e.getMessage()+"]");
        }

        //authinService.postLog(authinLogDto);
        authinService.insertLog(authinLogDto.getSiteCode(), authinLogDto.getUrl(), authinLogDto.getMessage(), authinLogDto.getIsClear());
    }


    public ResponseEntity<OnsiteResponse> askTTSMOnSite(SiteStatusRequest siteStatusRequest) {
        String siteCode = siteStatusRequest.getSite_code();

        // 0 - Don't know (no data), 1 - On site, 2 - NOT on site
        int is_onsite;
        String raw;

        TtsmJobStatus latest = ttsmWebhookService.getLatestJobStatus(siteCode);
        if (latest == null) {
            // ไม่มีข้อมูลเลย
            is_onsite = 0;
            raw = "No data in DB for site: " + siteCode;
            log.debug("askTTSMOnSite | Site: {} | No record found in DB", siteCode);
        } else {
            // มีข้อมูล → ใช้ logic ที่ถูกต้อง: เช็คว่ามี jobId ที่ยัง On-Site ค้างอยู่หรือไม่
            boolean hasActiveOnsite = ttsmWebhookService.isTechnicianOnsite(siteCode);
            if (hasActiveOnsite) {
                is_onsite = 1;
                raw = "On-Site";
                log.debug("askTTSMOnSite | Site: {} | Active On-Site job exists", siteCode);
            } else {
                is_onsite = 2;
                raw = latest.getJobStatus();
                log.debug("askTTSMOnSite | Site: {} | No active On-Site job | Latest: {} | JobId: {}",
                        siteCode, latest.getJobStatus(), latest.getJobId());
            }
        }

        return ResponseEntity.status(HttpStatus.OK).body(new OnsiteResponse(siteCode, is_onsite, raw));
    }

    public List<SiteEventLogWithCount> getResolveDateIsNullAllLogs(LocalDateTime startDate,LocalDateTime endDate,String keyword) {


        List<SiteEventLogWithCount> ret = new ArrayList<>();

        try{
            ret = siteEventLogCustomRepositoryImpl.getAllUnresolvedLogsWithCounts(keyword,startDate,endDate);

        }catch (Exception e)
        {

        }

        return ret;
    }


    public List<SiteEventLogWithResolve> getResolveDateAllLogs(LocalDateTime startDate,LocalDateTime endDate,String keyword,List<Integer> alarmTypes) {


        List<SiteEventLogWithResolve> ret = new ArrayList<>();

        try{
            ret = siteEventLogCustomRepositoryImpl.getAllResolvedLogsByAlarmTypes(keyword,alarmTypes,startDate,endDate);

        }catch (Exception e)
        {

        }

        return ret;
    }





}


