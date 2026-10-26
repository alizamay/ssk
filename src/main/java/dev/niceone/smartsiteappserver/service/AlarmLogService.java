package dev.niceone.smartsiteappserver.service;

import com.google.common.collect.Iterables;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import dev.niceone.smartsiteappserver.dto.AuthinLogDto;
import dev.niceone.smartsiteappserver.elastic.repository.ActivityLogRepository;
import dev.niceone.smartsiteappserver.exception.AppException;
import dev.niceone.smartsiteappserver.exception.ResourceNotFoundException;
import dev.niceone.smartsiteappserver.mapper.AlarmLogModelMapper;
import dev.niceone.smartsiteappserver.mapper.SiteModelMapper;
import dev.niceone.smartsiteappserver.model.*;
import dev.niceone.smartsiteappserver.model.constant.AlarmTypeCodeEnum;
import dev.niceone.smartsiteappserver.payload.*;
import dev.niceone.smartsiteappserver.payload.request.PiAlarmLogRequest;
import dev.niceone.smartsiteappserver.payload.request.SiteStatusRequest;
import dev.niceone.smartsiteappserver.payload.response.OnsiteResponse;
import dev.niceone.smartsiteappserver.repository.*;
import dev.niceone.smartsiteappserver.security.UserPrincipal;
import dev.niceone.smartsiteappserver.specification.AlarmLogSpecification;
import dev.niceone.smartsiteappserver.specification.SearchOperation;
import dev.niceone.smartsiteappserver.specification.SpecSearchCriteria;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Slf4j
@EnableAsync
public class AlarmLogService {
    private AlarmLogRepository alarmLogRepository;
    private CabinetRepository cabinetRepository;
    private DeviceRepository deviceRepository;
    private SiteRepository siteRepository;
    private UserRepository userRepository;

    @Autowired
    private Environment env;
    @Autowired
    private AuthinService authinService;
    @Autowired
    private TtsmWebhookService ttsmWebhookService;

    @Autowired
    public AlarmLogService(AlarmLogRepository alarmLogRepository,
                           CabinetRepository cabinetRepository,
                           DeviceRepository deviceRepository,
                           SiteRepository siteRepository,
                           UserRepository userRepository) {
        this.alarmLogRepository = alarmLogRepository;
        this.cabinetRepository = cabinetRepository;
        this.deviceRepository = deviceRepository;
        this.siteRepository = siteRepository;
        this.userRepository = userRepository;
    }

    AlarmTypeCodeEnum[] reportTypes = {
            AlarmTypeCodeEnum.ALARM_TYPE_VIBRAT_EXCESS,
            AlarmTypeCodeEnum.ALARM_TYPE_UNAUTH_OPENED,
            AlarmTypeCodeEnum.ALARM_TYPE_MOTION_EXCESS,
            AlarmTypeCodeEnum.ALARM_TYPE_COMM_LOST_SERV,
            AlarmTypeCodeEnum.ALARM_TYPE_COMM_LOST_PI,
            AlarmTypeCodeEnum.ALARM_TYPE_SLAVE_DOWN
    };

    public PagedResponse<AlarmLogResponse> getAlarmList(Specification<AlarmLog> spec, int page, int size,
                                                        UserPrincipal currentUser) {
        // User
        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new AppException("User not set."));

        // Site
        List<Long> siteIds = new ArrayList<>();
        user.getRegions().forEach((region) -> {
            region.getSites().forEach((site) -> {
                siteIds.add(site.getId());
            });
        });
        user.getSites().forEach((site) -> {
            siteIds.add(site.getId());
        });
//        List<Site> sites = siteRepository.findByIdIn(siteIds);

        // Cabinet
//        List<Long> cabinetIds = new ArrayList<>();
//        sites.forEach((site) -> {
//            site.getCabinets().forEach((cabinet) -> {
//                cabinetIds.add(cabinet.getId());
//            });
//        });
//        List<Cabinet> cabinets = cabinetRepository.findByIdIn(cabinetIds);

        // Device
//        List<Long> deviceIds = new ArrayList<>();
//        cabinets.forEach((cabinet) -> {
//            cabinet.getDevices().forEach((device) -> {
//                deviceIds.add(device.getId());
//            });
//        });

        AlarmLogSpecification siteSpec =
                new AlarmLogSpecification(new SpecSearchCriteria("site", SearchOperation.IN, siteIds));

//        AlarmLogSpecification cabinetSpec =
//                new AlarmLogSpecification(new SpecSearchCriteria("cabinet", SearchOperation.IN, cabinetIds));
//
//        AlarmLogSpecification deviceSpec =
//                new AlarmLogSpecification(new SpecSearchCriteria("device", SearchOperation.IN, deviceIds));

        Pageable pageable = PageRequest.of(page, size, Sort.Direction.DESC, "id");
//        Page<AlarmLog> alarmLogList = alarmLogRepository.findAll(spec.and(deviceSpec.or(cabinetSpec).or(siteSpec)), pageable);
        Page<AlarmLog> alarmLogList = alarmLogRepository.findAll(spec.and(siteSpec), pageable);

        List<AlarmLogResponse> alarmLogResponses = alarmLogList.map(alarmLog -> {
            return AlarmLogModelMapper.mapAlarmLogResponse(alarmLog);
        }).getContent();

//        return response;
        return new PagedResponse<>(alarmLogResponses, alarmLogList.getNumber(),
                alarmLogList.getSize(), alarmLogList.getTotalElements(), alarmLogList.getTotalPages(), alarmLogList.isLast());
    }

    @Async
    public void sendTcpAlarm(String alarm, AuthinLogDto authinLogDto) {
        log.info("Execute method sendTcpAlarm. "
                + Thread.currentThread().getName());
        String alarm_ip = env.getProperty("app.alarm_ip");
        String alarm_port = env.getProperty("app.alarm_port");

        log.info("sendTcpAlarm "+alarm_ip+" "+alarm_port);
        log.info(alarm);
        authinLogDto.setUrl(alarm_ip+":" + alarm_port);
        Socket socket = new Socket();
        SocketAddress sockAddr = new InetSocketAddress(alarm_ip, Integer.parseInt(alarm_port));
        try {
            socket.connect(sockAddr, 5000);
            OutputStream output = socket.getOutputStream();
            PrintWriter writer = new PrintWriter(output, true);
            writer.print(alarm);
            writer.flush();
            writer.close();
            socket.close();
            authinLogDto.setMessage(alarm + ",[SUCCESS]");


        } catch (UnknownHostException ex) {
            log.error("sendTcpAlarm Server not found: " + ex.getMessage());
            authinLogDto.setMessage(alarm + ",[FAIL:sendTcpAlarm Server not found "+ex.getMessage()+"]");
        } catch (IOException e) {
            log.error("sendTcpAlarm I/O error: " + e.getMessage());
            authinLogDto.setMessage(alarm + ",[FAIL: sendTcpAlarm I/O error"+e.getMessage()+"]");
        }

        authinService.insertLog(authinLogDto.getSiteCode(), authinLogDto.getUrl(), authinLogDto.getMessage(), authinLogDto.getIsClear());
        //authinService.postLog(authinLogDto);
    }

    public String tcpAlarmPayload(AlarmLog alarmLog) {
//        EMS NAME|EMS IP|SYSTEM NAME|NODE NAME|NODE IP|ALARM ID|ALARM NAME|ALARM LOCATION|SEVERITY|ALARM DESCRIPTION|DATE TIME
//
//        SMARTSITE|10.242.15.22|SMARTSITE|BKKYM|10.242.34.46|Communication Lost|Communication_Lost|SmartSite Master device|major|Communication Loss from site|20210110 12:00:00
//
//        SMARTSITE|10.242.15.22|SMARTSITE|BKKYM|10.242.34.46|Communication Lost|Communication_Lost|SmartSite Master device|clear|Communication Loss from site|20210110 12:00:00

//        String localhost = "localhost";
//        String hostname = "localhost";
//        try {
//            localhost = InetAddress.getLocalHost().getHostAddress();
//            hostname = InetAddress.getLocalHost().getHostName();
//        } catch (UnknownHostException e) {
//            e.printStackTrace();
//        }

        String my_ip = env.getProperty("app.my_ip");
        String alarm_postfix = env.getProperty("app.alarm_postfix");
        String localhost = "10.242.15.22";

        if (my_ip != null) {
            localhost = my_ip;
        }

        String hostname = "SMARTSITE";

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss");
        String formatDateTime = LocalDateTime.now().format(formatter);
        if (alarmLog.getUpdatedAt() != null) {
            formatDateTime = alarmLog.getUpdatedAt().format(formatter);
        }

        String severity = alarmLog.getAlarmType().severity;
        if(alarmLog.getResolveDate() != null) {
            severity = "clear";
            formatDateTime = alarmLog.getResolveDate().format(formatter);
        }

        String str = "SMARTSITE|" +
            localhost +"|" +
            hostname + "|" +
            alarmLog.getSite().getCode() + "|" +
            alarmLog.getSite().getIp_address() + "|" +
            alarmLog.getAlarmType().aKey + "|" +
            alarmLog.getAlarmType().aName + "|" +
            "SmartSite Master device|" +
            severity + "|" +
            alarmLog.getAlarmType().name + "|" +
            formatDateTime + alarm_postfix;

        return str;
    }

    // Pi Update or Create
    public ResponseEntity<ApiResponse> piAlarm(PiAlarmLogRequest alarmLogRequest) {
        AlarmLog alarmLog = null;
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
        List<AlarmLog> cabinetAlarms = alarmLogRepository.findByCabinetAndAlarmTypeAndResolveDateIsNull(cabinet, alarmType);
        alarmLog = Iterables.getLast(cabinetAlarms, null);

        // case resolve
        if (alarmLog != null && alarmLogRequest.getStatus() == 0) {

            if(alarmLogRequest.getAlarm_id() == 18){
                if(cabinet != null){
                    cabinet.setOnline(true);
                    cabinetRepository.saveAndFlush(cabinet);
                }

            }
            // Found un-resolve alarm
            alarmLog.setWriteToFile(false);
            alarmLog.setResolveDate(LocalDateTime.now());
            alarmLogRepository.save(alarmLog);
            if (Arrays.asList(reportTypes).contains(alarmLog.getAlarmType())){
                String msgAlarm = tcpAlarmPayload(alarmLog);
                AuthinLogDto aDto = new AuthinLogDto();
                aDto.setSiteCode(alarmLog.getSite().getCode());
                aDto.setIsClear(true);
                sendTcpAlarm(msgAlarm,aDto);
            }

            return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse(true, "UPDATE OK"));
        }

        // new alarm
        if (alarmLog == null && alarmLogRequest.getStatus() == 1) {

            if(alarmLogRequest.getAlarm_id() == 18){
                if(cabinet != null){
                    cabinet.setOnline(false);
                    cabinetRepository.saveAndFlush(cabinet);
                }
            }

            alarmLog = new AlarmLog();
            alarmLog.setActive(true);
            alarmLog.setSite(site);
            alarmLog.setCabinet(cabinet);
            alarmLog.setAlarmType(AlarmTypeCodeEnum.getById(alarmLogRequest.getAlarm_id()));
            alarmLogRepository.save(alarmLog);
            log.info("Execute piAlarm response. "
                    + Thread.currentThread().getName());
            if (Arrays.asList(reportTypes).contains(alarmLog.getAlarmType())){
                String msgAlarm = tcpAlarmPayload(alarmLog);
                AuthinLogDto aDto = new AuthinLogDto();
                aDto.setSiteCode(alarmLog.getSite().getCode());
                aDto.setIsClear(false);
                sendTcpAlarm(msgAlarm,aDto);
            }

            return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse(true, "CREATE OK"));
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse(false, "Bad request"));
    }

    // Create
    public ResponseEntity<ApiResponse> createAlarm(AlarmLogRequest alarmLogRequest) {
        AlarmLog alarmLog = new AlarmLog();
        alarmLog.setActive(true);
        alarmLog.setAlarmType(AlarmTypeCodeEnum.getById(alarmLogRequest.getAlarm_type_id()));
        alarmLog.setDevice(deviceRepository.findById(alarmLogRequest.getDevice_id()).orElse(null));
        alarmLogRepository.save(alarmLog);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest().path("/{id}")
                .buildAndExpand(alarmLog.getId()).toUri();
        return ResponseEntity.created(location)
                .body(new ApiResponse(true, "AlarmLog Created Successfully"));
    }

    // Update
    public ResponseEntity<ApiResponse> updateResolveAlarm(Long id) {
        AlarmLog alarmLog = alarmLogRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("AlarmLog", "id", id));
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse(true, "UPDATE OK"));
    }

    public AlarmLog findUnResolveAlarmByCabinet(AlarmTypeCodeEnum alarmType, Cabinet cabinet) {
        AlarmLog alarmLog = null;
        if(cabinet != null) {
            List<AlarmLog> cabinetAlarms = alarmLogRepository.findByCabinetAndAlarmTypeAndResolveDateIsNull(cabinet, alarmType);
            alarmLog = Iterables.getLast(cabinetAlarms, null);
        }
        return alarmLog;
    }

    public AlarmLog getOrCreateAlarmLog(AlarmTypeCodeEnum alarmType, Site site, Cabinet cabinet, Device device, LocalDateTime resolveDate) {
        AlarmLog alarmLog = null;
        if (site != null) {
            List<AlarmLog> siteAlarms = alarmLogRepository.findBySiteAndAlarmTypeAndResolveDateIsNull(site, alarmType);
            alarmLog = Iterables.getLast(siteAlarms, null);
        } else if(cabinet != null) {
            List<AlarmLog> cabinetAlarms = alarmLogRepository.findByCabinetAndAlarmTypeAndResolveDateIsNull(cabinet, alarmType);
            alarmLog = Iterables.getLast(cabinetAlarms, null);
        } else if(device != null) {
            List<AlarmLog> deviceAlarms = alarmLogRepository.findByDeviceAndAlarmTypeAndResolveDateIsNull(device, alarmType);
            alarmLog = Iterables.getLast(deviceAlarms, null);
        }

        if (alarmLog != null && resolveDate != null) {
            // Found un-resolve alarm
            alarmLog.setWriteToFile(false);
            alarmLog.setResolveDate(resolveDate);
            alarmLogRepository.save(alarmLog);
//            if (Arrays.asList(reportTypes).contains(alarmLog.getAlarmType()))
//                sendTcpAlarm(tcpAlarmPayload(alarmLog));
        }

        if (alarmLog == null && resolveDate == null) {
            alarmLog = new AlarmLog(alarmType, site, cabinet, device, null);
            alarmLogRepository.save(alarmLog);
//            if (Arrays.asList(reportTypes).contains(alarmLog.getAlarmType()))
//                sendTcpAlarm(tcpAlarmPayload(alarmLog));
        }

        if (alarmLog != null) {
            alarmLogRepository.save(alarmLog);
        }
        return alarmLog;
    }

    public List<AlarmLog> getAllUnReportedAlarm() {
        return alarmLogRepository.findAllByWriteToFileIsFalse();
    }

    public List<AlarmLog> getAllByAlarmType(AlarmTypeCodeEnum alarmType, Boolean isResolve) {
        if (isResolve) {
            return alarmLogRepository.findAllByAlarmTypeAndResolveDateIsNotNull(alarmType);
        } else {
            // un-resolve alarm
            return alarmLogRepository.findAllByAlarmTypeAndResolveDateIsNull(alarmType);
        }
    }

    public ResponseEntity<OnsiteResponse> askTTSMOnSite(SiteStatusRequest siteStatusRequest) {
        String siteCode = siteStatusRequest.getSite_code();

        // 0 - Don't know, 1 - On site, 2 - NOT on site
        int is_onsite;
        String raw;

        dev.niceone.smartsiteappserver.model.TtsmJobStatus latest = ttsmWebhookService.getLatestJobStatus(siteCode);
        if (latest == null) {
            is_onsite = 0;
            raw = "No data in DB for site: " + siteCode;
            log.info("askTTSMOnSite | Site: {} | No record found in DB", siteCode);
        } else if ("On-Site".equals(latest.getJobStatus())) {
            is_onsite = 1;
            raw = latest.getJobStatus();
            log.info("askTTSMOnSite | Site: {} | Status: On-Site | JobId: {}", siteCode, latest.getJobId());
        } else {
            is_onsite = 2;
            raw = latest.getJobStatus();
            log.info("askTTSMOnSite | Site: {} | Status: {} | JobId: {}", siteCode, latest.getJobStatus(), latest.getJobId());
        }

        return ResponseEntity.status(HttpStatus.OK).body(new OnsiteResponse(siteCode, is_onsite, raw));
    }
}
