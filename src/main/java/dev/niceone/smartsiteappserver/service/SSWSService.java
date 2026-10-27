package dev.niceone.smartsiteappserver.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import dev.niceone.smartsiteappserver.elastic.repository.ActivityLogRepository;
import dev.niceone.smartsiteappserver.mapper.SiteModelMapper;
import dev.niceone.smartsiteappserver.model.*;
import dev.niceone.smartsiteappserver.model.constant.*;
import dev.niceone.smartsiteappserver.payload.ApiResponse;
import dev.niceone.smartsiteappserver.payload.IOReport;
import dev.niceone.smartsiteappserver.payload.WebSocketPayload;
import dev.niceone.smartsiteappserver.payload.request.OldImgUploadRequest;
import dev.niceone.smartsiteappserver.payload.request.ReportDeviceRequest;
import dev.niceone.smartsiteappserver.payload.request.StatusReportRequest;
import dev.niceone.smartsiteappserver.payload.response.SiteResponseForWs;
import dev.niceone.smartsiteappserver.repository.*;
import dev.niceone.smartsiteappserver.utils.Callback;
import dev.niceone.smartsiteappserver.utils.Debouncer;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
@Slf4j
@EnableAsync
public class SSWSService {
    private ActivityLogRepository activityLogRepository;
    private AlarmLogRepository alarmLogRepository;
    private CabinetRepository cabinetRepository;
    private DeviceRepository deviceRepository;
    private SiteRepository siteRepository;
    private ScheduledExecutorService taskScheduler;
    private Debouncer<StatusReportRequest> debouncer;

    @Autowired
    public SSWSService(ActivityLogRepository activityLogRepository,
                       AlarmLogRepository alarmLogRepository,
                       CabinetRepository cabinetRepository,
                       DeviceRepository deviceRepository,
                       SiteRepository siteRepository) {
        this.activityLogRepository = activityLogRepository;
        this.alarmLogRepository = alarmLogRepository;
        this.cabinetRepository = cabinetRepository;
        this.deviceRepository = deviceRepository;
        this.siteRepository = siteRepository;
        this.debouncer = new Debouncer(callback, 2000);
    }

    // Stop the debouncer thread on undeploy/redeploy so WildFly does not accumulate threads.
    @PreDestroy
    public void shutdownDebouncer() {
        debouncer.terminate();
    }

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private AlarmLogService alarmLogService;

    @Autowired
    private CamImgLogService camImgLogService;

    @Autowired
    private Environment env;

    @Autowired
    private AlarmLogCasService alarmLogCasService;
    @Autowired
    CacheManager cacheManager;

    final Callback callback = (key, extra) -> {
        StatusReportRequest request = (StatusReportRequest) extra;
//        System.out.println(new Date()+" Callback Task with "+key);
        markSiteUp(request);
        asyncReport(request);
    };

    public ResponseEntity<ApiResponse> forceUpdateCabinetStatus(StatusReportRequest request) {
        syncProcessReport(request);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse(true, "UPDATE OK"));
    }

    public ResponseEntity<ApiResponse> updateCabinetStatus(StatusReportRequest request) {
        return updateCabinetStatus_old(request);
    }

    public ResponseEntity<ApiResponse> updateCabinetStatus_old(StatusReportRequest request) {
//        log.info("updateCabinetStatus "+request.getSite_code());

        this.debouncer.call(request.getSite_code(), request);
//        asyncReport(request);
//        markSiteUp(request);

//        wsAnnounceToHome(request);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse(true, "UPDATE OK"));
    }

    @Transactional
    public ResponseEntity<ApiResponse> ioReport(IOReport request) {
        Cabinet cabinet = cabinetRepository.findByCode(request.getCabinet_code());
        if (cabinet == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(false, "Cabinet not found"));
        }

        AtomicReference<Boolean> isModified = new AtomicReference<>(false);

        List<AuxDevice> auxes = cabinet.getAux_devices();
        log.debug("auxes {}", auxes);
        if (auxes == null || auxes.isEmpty()) {
            if (cabinet.getCabinetNo() == 1) {
                cabinet.addAuxDevice(new AuxDevice(0, false));
                cabinet.addAuxDevice(new AuxDevice(1, true));
                cabinet.addAuxDevice(new AuxDevice(2, true));
                cabinet.addAuxDevice(new AuxDevice(3, true));
            } else {
                cabinet.addAuxDevice(new AuxDevice(0, false));
            }
            cabinetRepository.save(cabinet);
            auxes = cabinet.getAux_devices();
        }
        auxes.forEach((aux) -> {
            if (aux.getAuxNo() == 1) {
                if (aux.getIs_on() != request.getAuxin1()) {
                    aux.setIs_on(request.getAuxin1());
                    isModified.set(true);
                }
            } else if(aux.getAuxNo() == 2) {
                if (aux.getIs_on() != request.getAuxin2()) {
                    aux.setIs_on(request.getAuxin2());
                    isModified.set(true);
                }
            } else if(aux.getAuxNo() == 3) {
                if (aux.getIs_on() != request.getAuxin3()) {
                    aux.setIs_on(request.getAuxin3());
                    isModified.set(true);
                }
            }
        });

        if (isModified.get())
            cabinetRepository.saveAndFlush(cabinet);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse(true, "UPDATE OK"));
    }

    private boolean updateLockEvent(Cabinet cabinet, LockEventCodeEnum lockEvent, LocalDateTime finalDate) {
        if (cabinet.getLockEventCode() != lockEvent) {
            cabinet.setLockEventCode(lockEvent);
            cabinet.setUpdatedAt(finalDate);
            cabinetRepository.saveAndFlush(cabinet);
            return true;
        }
        return false;
    }

    private boolean updateDoorEvent(Cabinet cabinet, DoorEventCodeEnum doorEvent, LocalDateTime finalDate) {
        if (cabinet.getDoorEventCode() != doorEvent) {
            cabinet.setDoorEventCode(doorEvent);
            cabinet.setUpdatedAt(finalDate);
            cabinetRepository.saveAndFlush(cabinet);
            return true;
        }
        return false;
    }

    private boolean updateLockEventNew(Cabinet cabinet, LockEventCodeEnum lockEvent, LocalDateTime finalDate) {
        if (cabinet.getLockEventCode() != lockEvent) {
            cabinet.setLockEventCode(lockEvent);
            cabinet.setUpdatedAt(finalDate);
            //cabinetRepository.saveAndFlush(cabinet);
            return true;
        }
        return false;
    }

    private boolean updateDoorEventNew(Cabinet cabinet, DoorEventCodeEnum doorEvent, LocalDateTime finalDate) {
        if (cabinet.getDoorEventCode() != doorEvent) {
            cabinet.setDoorEventCode(doorEvent);
            cabinet.setUpdatedAt(finalDate);
            //cabinetRepository.saveAndFlush(cabinet);
            return true;
        }
        return false;
    }

    @Async
    private void _processReport(StatusReportRequest request) {
        //        try {
//            ObjectMapper mapper = new ObjectMapper();
//            String jsonString = mapper.writeValueAsString(request);
//            log.info("" + jsonString);
//        } catch (Exception e) {
//            log.info("writeValueAsString error");
//        }
        //log.info("process start from board site update status "+request.getSite_code()+" ");
        List<String> allCabinetCodes = request.getDevices().stream().map(req -> {
            return req.getCabinet_code();
        }).collect(Collectors.toList());
        List<String> cabinetCodes = allCabinetCodes.stream().distinct().collect(Collectors.toList());
        List<Cabinet> cabinets = cabinetRepository.findByCodeIn(cabinetCodes);
        //Site site = siteRepository.findByCode(request.getSite_code()).get();
        //List<Device> devices = deviceRepository.findByCabinetInAndActiveIsTrue(cabinets);

        //log.info("process loop cabinet from board start site update status "+request.getSite_code()+" ");
        // Update
        cabinets.stream().forEach(cabinet -> {
            List<ReportDeviceRequest> deviceReqs = request.getDevices()
                    .stream()
                    .filter(d -> d.getCabinet_code().equals(cabinet.getCode()))
                    .collect(Collectors.toList());

            List<Device> lsDevice = new ArrayList<>();
            //log.info("process loop device from board start site update status "+request.getSite_code()+" ");

            ReportDeviceRequest lock_device = deviceReqs.stream().filter(d -> d.getDevice_type_id() == DeviceTypeCodeOld.KEY_SWITCH.id).findFirst().orElse(null);

            if(lock_device != null){
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                LocalDateTime finalDate = LocalDateTime.parse(lock_device.getDt(), formatter);

                if (lock_device.getEvent_type_id() == EventTypeCodeEnum.EVENT_TYPE_KEY_SWITCH_LOCKED.id) {
                    updateLockEventNew(cabinet, LockEventCodeEnum.LOCK_EVENT_LOCK, finalDate);
                } else if (lock_device.getEvent_type_id() == EventTypeCodeEnum.EVENT_TYPE_KEY_SWITCH_UNLOCKED.id) {
                    updateLockEventNew(cabinet, LockEventCodeEnum.LOCK_EVENT_UNLOCK, finalDate);
                }
                //log.info("process update lock device from board complete site update status "+request.getSite_code()+" ");
            }

            ReportDeviceRequest door_device = deviceReqs.stream().filter(d -> d.getDevice_type_id() == DeviceTypeCodeOld.DOOR_SWITCH.id).findFirst().orElse(null);

            if(door_device != null){
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                LocalDateTime finalDate = LocalDateTime.parse(door_device.getDt(), formatter);

                if (door_device.getEvent_type_id() == EventTypeCodeEnum.EVENT_TYPE_DOOR_SWITCH_CLOSED.id) {
                    if(cabinet.getDoorEventCode() == DoorEventCodeEnum.DOOR_EVENT_OPEN){
                        SendResolveDoor(request, cabinet);
                    }
                    updateDoorEventNew(cabinet, DoorEventCodeEnum.DOOR_EVENT_CLOSE, finalDate);

                } else if (door_device.getEvent_type_id() == EventTypeCodeEnum.EVENT_TYPE_DOOR_SWITCH_OPENED.id) {
                    updateDoorEventNew(cabinet, DoorEventCodeEnum.DOOR_EVENT_OPEN, finalDate);
                    if(cabinet.getLockEventCode() == LockEventCodeEnum.LOCK_EVENT_LOCK) {
                        SendLog(request, cabinet);
                    }
//
                }

                //log.info("process update lock device from board complete site update status "+request.getSite_code()+" ");
            }


            List<ReportDeviceRequest> reportDevice = deviceReqs.stream().filter(x -> x.getCabinet_code().equals(cabinet.getCode())).collect(Collectors.toList());
//            reportDevice.forEach(deviceReq -> {
//                Device d = devices.stream().filter(x->x.getDeviceTypeCode().id == deviceReq.getDevice_type_id()).findFirst().orElse(null);
//                if(d != null){
//                    if(d.getCabinet().getId().equals(cabinet.getId())){
//                        d.setStatus(StatusCodeEnum.getById(deviceReq.getDevice_status_id()));
//                        d.setEventType(EventTypeCodeEnum.getById(deviceReq.getEvent_type_id()));
//                        d.setUpdatedAt(LocalDateTime.now());
//                        lsDevice.add(d);
//                    }
//
//                }
//            });


            //log.info("process update device req in cabinet count " + reportDevice.stream().count());
            //log.info("process update device all in cabinet count " + lsDevice.stream().count());
            //log.info("process update device all count " + devices.stream().count());

            ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
            try {
                String json = ow.writeValueAsString(reportDevice);
                cabinet.setJsonDevices(json);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
            //cabinet.setOnline(true);
            cabinet.setUpdatedAt(LocalDateTime.now());
            //cabinet.getDevices().forEach();
            cabinetRepository.saveAndFlush(cabinet);
            log.debug("process end update cabinet all from board complete site update status {}", request.getSite_code());

        });
    }

    @Async
    void SendLog(StatusReportRequest request,Cabinet cabinet){
        try {
            alarmLogCasService.getOrCreateAlarmLog(AlarmTypeCodeEnum.ALARM_TYPE_UNAUTH_OPENED, request.getSite_code(), cabinet, null, null);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Async
    void SendResolveDoor(StatusReportRequest request,Cabinet cabinet){
        try {
            alarmLogCasService.getOrCreateAlarmLog(AlarmTypeCodeEnum.ALARM_TYPE_UNAUTH_OPENED, request.getSite_code(), cabinet, null, LocalDateTime.now());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Async
    void asyncReport(StatusReportRequest request) {
        _processReport(request);
    }

    void syncProcessReport(StatusReportRequest request) {
        _processReport(request);
    }

    @Async
    void markSiteUp(StatusReportRequest request) throws JsonProcessingException {
        Site site = siteRepository.findByCode(request.getSite_code()).orElse(null);
        if (site == null) {
//            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(false, "Site not found"));
            return;
        }

        boolean checkBeforeOffline = site.getStatus() == StatusCodeEnum.STATUS_DOWN;

        site.setStatus(StatusCodeEnum.STATUS_UP);
        site.setTemperature(request.getSite_temperature());
        site.setVersion(request.getSite_version());
        site.setReportStatusAt(LocalDateTime.now());

        try {
            siteRepository.save(site);

            if(checkBeforeOffline){
                alarmLogCasService.getOrCreateAlarmLog(AlarmTypeCodeEnum.ALARM_TYPE_COMM_LOST_SERV, request.getSite_code(), null, null, LocalDateTime.now());
            }

            //log.info("save report status online site "+site.getCode());
        }catch (Exception ex){
            log.warn("save report status online error site {}: {}", site.getCode(), ex.getMessage());
        }

    }

    @Async
    void wsAnnounceToInformation(Site site) {
        SiteResponseForWs response = SiteModelMapper.mapSiteToSiteResponseForWs(site);
        messagingTemplate.convertAndSend("/topic/site_report", response);
    }

    @Async
    void wsAnnounceToHome(StatusReportRequest request) {
        JSONObject wsJson = new JSONObject();
//        String msg = deviceReq.getDevice_type_id() + " " + deviceReq.getEvent_type_id() + " " + deviceReq.getDevice_status_id();
        wsJson
                .put("message", request);
        WebSocketPayload payload = null;
        try {
            payload = new WebSocketPayload(request.getSite_code(), request);
            messagingTemplate.convertAndSend("/topic/pi_report", payload);
            // this message will show on Home ui
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }


    public ResponseEntity<ApiResponse> oldImgUpload(OldImgUploadRequest request) {

        saveImageUpload(request);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse(true, "UPLOAD OK"));
    }

    @Async
    void saveImageUpload(OldImgUploadRequest request){
        log.info("oldImgUpload "+request.getCabinet_code());
        String img_snapshot_path = env.getProperty("app.img_snapshot_path");

        String input_cabinetCode = request.getCabinet_code();
        Cabinet cabinet = cabinetRepository.findByCode(input_cabinetCode);
        if (cabinet == null) {
            //log.info("oldImgUpload not found cabinet "+request.getCabinet_code());
           // return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(false, "Cabinet not found"));
        }

        if (cabinet.getSite() == null) {
            //log.info("oldImgUpload site not found "+request.getCabinet_code());
           // return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(false, "Site not found"));
        }

        Site site = cabinet.getSite();
//        String input_siteCode = request.getSite_code();
        String input_img = request.getImg_data();
        String input_dt = request.getDt();
        String input_deviceTypeId = request.getDevice_type_id();
        String input_captureTime = request.getCapture_time();

        //Format Date time
//        String yyyy = input_dt.substring(0,4);
//        String mm = input_dt.substring(5,7);
//        String dd = input_dt.substring(8,10);

        String hh = input_dt.substring(11,13);
        String min = input_dt.substring(14,16);
        String ss = input_dt.substring(17);

        String sRootPath = "";

        //Real path for saving files in web app server
        //sRootPath = "/img_upload";
        sRootPath = img_snapshot_path;

        LocalDateTime nowDate = LocalDateTime.now();

        String yyyy = String.valueOf(nowDate.getYear());

        Month currentMonth = nowDate.getMonth();
        String mm = String.format("%02d", currentMonth.getValue());

        Calendar calendar = Calendar.getInstance();
        int currentDay = calendar.get(Calendar.DAY_OF_MONTH);
        String dd = String.format("%02d", currentDay);

        String newPath = "/" + input_cabinetCode + "/" + yyyy +"/"+ mm +"/"+ dd + "/" ;
        // new File("").getAbsolutePath();
        String fileName = input_cabinetCode+"_"+yyyy+mm+dd+input_captureTime+".jpg";

        // Save to server's disk
        //
        byte[] decodedImg = Base64.getDecoder().decode(input_img.getBytes(StandardCharsets.UTF_8));
        java.nio.file.Path destinationFile = Paths.get(sRootPath+newPath, fileName);

        try {
            if(!Files.exists(destinationFile.getParent()))
                Files.createDirectories(destinationFile.getParent());
            Files.write(destinationFile, decodedImg);
        } catch (IOException e) {
            e.printStackTrace();
           // return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse(true, e.getMessage()));
        }

        // find ALARM_TYPE_UNAUTH_OPENED
        //AlarmLog alarmLog = alarmLogService.findUnResolveAlarmByCabinet(AlarmTypeCodeEnum.ALARM_TYPE_UNAUTH_OPENED, cabinet);

        // Save to database
        //camImgLogService.createCamImgLog(site, cabinet, newPath, fileName, alarmLog);
    }
}