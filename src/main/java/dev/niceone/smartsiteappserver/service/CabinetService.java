package dev.niceone.smartsiteappserver.service;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import dev.niceone.smartsiteappserver.elastic.repository.ActivityLogRepository;
import dev.niceone.smartsiteappserver.model.*;
import dev.niceone.smartsiteappserver.model.constant.*;
import dev.niceone.smartsiteappserver.payload.ApiResponse;
import dev.niceone.smartsiteappserver.payload.CabinetStatusResponse;
import dev.niceone.smartsiteappserver.payload.CabinetStatusResponses;
import dev.niceone.smartsiteappserver.payload.request.CabinetLockRequest;
import dev.niceone.smartsiteappserver.payload.request.ReportDeviceRequest;
import dev.niceone.smartsiteappserver.payload.request.SiteStatusRequest;
import dev.niceone.smartsiteappserver.payload.request.StatusReportRequest;
import dev.niceone.smartsiteappserver.repository.*;
import dev.niceone.smartsiteappserver.utils.AppConstants;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.client.reactive.ReactorResourceFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.resources.LoopResources;
import reactor.netty.tcp.TcpClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CabinetService {
    private ActivityLogRepository activityLogRepository;
    private CabinetRepository cabinetRepository;
    private DeviceRepository deviceRepository;
    private SiteRepository siteRepository;
    private SSWSService sswsService;
    private ReactorResourceFactory reactorResourceFactory;
    private WebClient webClient;

    private SiteVersionRepository siteVersionRepository;
    @Autowired
    private Environment env;

    @Autowired
    public CabinetService(ActivityLogRepository activityLogRepository,
                          CabinetRepository cabinetRepository,
                          DeviceRepository deviceRepository,
                          SiteRepository siteRepository,
                          SiteVersionRepository siteVersionRepository,
                          SSWSService sswsService) {
        this.activityLogRepository = activityLogRepository;
        this.cabinetRepository = cabinetRepository;
        this.deviceRepository = deviceRepository;
        this.siteRepository = siteRepository;
        this.siteVersionRepository = siteVersionRepository;
        this.sswsService = sswsService;
        TcpClient tcpClient = TcpClient
                .create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 15000)
                .doOnConnected(connection -> {
                    connection.addHandlerLast(new ReadTimeoutHandler(15000, TimeUnit.MILLISECONDS));
                    connection.addHandlerLast(new WriteTimeoutHandler(15000, TimeUnit.MILLISECONDS));
                });
        //HACK for the issue: Connection prematurely closed BEFORE response
        final String poolName = "fixed";
        this.reactorResourceFactory = new ReactorResourceFactory();
        this.reactorResourceFactory.setUseGlobalResources(false);
        this.reactorResourceFactory.setLoopResourcesSupplier(() -> LoopResources.create(poolName, 2, true));
        this.reactorResourceFactory.setConnectionProviderSupplier(() -> ConnectionProvider.fixed(poolName, 10));
        this.reactorResourceFactory.afterPropertiesSet();

        webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(this.reactorResourceFactory, httpClient -> HttpClient.from(tcpClient)))
                .build();
    }

    private HashMap<String, String> callLockAPIonRealDevice(String ip, int port, String cabinet_code) {
        String targetUrl = "http://"+ip+":"+port+AppConstants.LOCK_CABINET_URL;
        log.info("call Lock API on Real Device "+targetUrl+" "+cabinet_code);

        HashMap<String, String> result = new HashMap<>();
        String errMsg = "";
        boolean isError = false;

        String apikey_for_device = env.getProperty("apikey_for_device");

        try {
            HttpResponse<String> response = Unirest.post(targetUrl)
                    .header("Content-Type", "application/json")
                    .header("apiKey", apikey_for_device)
                    .body("{\"cabinet_code\": \"" + cabinet_code + "\"}")
                    .asString();

            // Retrieve the parsed JSONObject from the response
            String responseString = response.getBody();
            log.info("responseString: "+responseString);
            JSONObject jsonResp = new JSONObject(responseString);
            boolean is_success = (boolean) jsonResp.get("is_success");
            if (is_success == true) {
                isError = false;
            } else {
                isError = true;
            }
            errMsg = String.valueOf( jsonResp.get("err_msg") );
            log.info("msg "+errMsg);
        } catch (UnirestException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            isError = true;
            errMsg = "Failed to communicate with targeted site.\n " + ip+":"+port;
            log.info("errMsg "+errMsg);
        }

        result.put("isError", ""+isError);
        result.put("errMsg", errMsg);
        log.info("return result: "+result);
        return result;
    }

    private Map<String, String> callUnLockAPIonRealDevice(String ip, int port, String cabinet_code) {
        String targetUrl = "http://"+ip+":"+port+AppConstants.UNLOCK_CABINET_URL;
        log.info("call UnLock API on Real Device "+targetUrl+" "+cabinet_code);

        HashMap<String, String> result = new HashMap<>();
        String errMsg = "";
        boolean isError = false;
        String apikey_for_device = env.getProperty("apikey_for_device");
        try {
            HttpResponse<String> response = Unirest.post(targetUrl)
                    .header("Content-Type", "application/json")
                    .header("apiKey", apikey_for_device)
                    .body("{\"cabinet_code\": \"" + cabinet_code + "\"}")
                    .asString();

            // Retrieve the parsed JSONObject from the response
            String responseString = response.getBody();
            log.info("responseString: "+responseString);
            JSONObject jsonResp = new JSONObject(responseString);
            boolean is_success = (boolean) jsonResp.get("is_success");
            if (is_success == true) {
                isError = false;
            } else {
                isError = true;
            }
            errMsg = String.valueOf( jsonResp.get("err_msg") );
            log.info("msg "+errMsg);
        } catch (UnirestException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            isError = true;
            errMsg = "Failed to communicate with targeted site.\n" + ip+":"+port;
            log.info("errMsg "+errMsg);
        }

        result.put("isError", ""+isError);
        result.put("errMsg", errMsg);
        log.info("return result: "+result);
        return result;
    }

    public ResponseEntity<ApiResponse> keyLock(CabinetLockRequest cabinetRequest) {
        log.info("KEY Lock " + cabinetRequest.getCabinet_code());
        Cabinet cabinet = cabinetRepository.findByCode(cabinetRequest.getCabinet_code());
        String ip = cabinet.getSite().getIp_address();
        int port = cabinet.getSite().getPort();

        ActivityLog activityLogAuthorized = new ActivityLog("lockCabinet", "LOCK", "Lock Cabinet",
                "INFO", "Authorized");
//        activityLogRepository.save(activityLogAuthorized);

        Map<String, String> callResult = callLockAPIonRealDevice(ip, port, cabinetRequest.getCabinet_code());

        if (callResult.get("isError").equals("true")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    new ApiResponse(false, callResult.get("errMsg"))
            );
        }

        if (callResult.get("isError").equals("false")) {
            StatusReportRequest statusReportRequest = new StatusReportRequest();
            statusReportRequest.setSite_code(cabinet.getSite().getCode());

            ReportDeviceRequest reportDeviceRequest = new ReportDeviceRequest();
            ReportDeviceRequest reportDeviceRequest2 = new ReportDeviceRequest();

            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String date = now.format(formatter);

            reportDeviceRequest.setSerial("");
            reportDeviceRequest.setCabinet_code(cabinet.getCode());
            reportDeviceRequest.setDt(date);
            reportDeviceRequest.setDevice_status_id(1);
            reportDeviceRequest.setDevice_status(StatusCodeEnum.STATUS_UP.name);
            reportDeviceRequest.setDevice_type_id(10);
            reportDeviceRequest.setDevice_type(DeviceTypeCodeEnum.DEVICE_TYPE_KEY_SWITCH.name);
            reportDeviceRequest.setEvent_type_id(10);
            reportDeviceRequest.setEvent_type(EventTypeCodeEnum.EVENT_TYPE_KEY_SWITCH_LOCKED.name);

            reportDeviceRequest2.setSerial("");
            reportDeviceRequest2.setCabinet_code(cabinet.getCode());
            reportDeviceRequest2.setDt(date);
            reportDeviceRequest2.setDevice_status_id(1);
            reportDeviceRequest2.setDevice_status(StatusCodeEnum.STATUS_UP.name);
            reportDeviceRequest2.setDevice_type_id(1);
            reportDeviceRequest2.setDevice_type(DeviceTypeCodeEnum.DEVICE_TYPE_SMART_LOCK.name);
            reportDeviceRequest2.setEvent_type_id(1);
            reportDeviceRequest2.setEvent_type(EventTypeCodeEnum.EVENT_TYPE_SMART_LOCK_LOCKED.name);

            List<ReportDeviceRequest> devices = new ArrayList<>();
            devices.add(reportDeviceRequest);
            devices.add(reportDeviceRequest2);
            statusReportRequest.setDevices(devices);
            sswsService.forceUpdateCabinetStatus(statusReportRequest);

            ActivityLog activityLogSuccess = new ActivityLog("lockCabinet", "LOCK", callResult.get("errMsg"),
                    "INFO", "Success");
//            activityLogRepository.save(activityLogSuccess);

            return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse(true, "UPDATE OK"));
        } else {
            ActivityLog activityLogFail = new ActivityLog("lockCabinet", "LOCK", callResult.get("errMsg"),
                    "INFO", "Fail");
//            activityLogRepository.save(activityLogFail);

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse(false, "UPDATE FAILED"));
        }
    }

    public ResponseEntity<ApiResponse> keyUnlock(CabinetLockRequest cabinetRequest) {
//        Device device = deviceRepository.findById(id).orElseThrow(
//                () -> new ResourceNotFoundException("Device", "id", id));
        log.info("KEY Unlock " + cabinetRequest.getCabinet_code());
        Cabinet cabinet = cabinetRepository.findByCode(cabinetRequest.getCabinet_code());
        String ip = cabinet.getSite().getIp_address();
        int port = cabinet.getSite().getPort();

        ActivityLog activityLogAuthorized = new ActivityLog("unlockCabinet", "UNLOCK", "Unlock Cabinet",
                "INFO", "Authorized");
//        activityLogRepository.save(activityLogAuthorized);

        Map<String, String> callResult = callUnLockAPIonRealDevice(ip, port, cabinetRequest.getCabinet_code());

        if (callResult.get("isError").equals("true")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    new ApiResponse(false, callResult.get("errMsg"))
            );
        }

        if (callResult.get("isError").equals("false")) {
            StatusReportRequest statusReportRequest = new StatusReportRequest();
            statusReportRequest.setSite_code(cabinet.getSite().getCode());

            ReportDeviceRequest reportDeviceRequest = new ReportDeviceRequest();
            ReportDeviceRequest reportDeviceRequest2 = new ReportDeviceRequest();

            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String date = now.format(formatter);
            reportDeviceRequest.setSerial("");
            reportDeviceRequest.setCabinet_code(cabinet.getCode());
            reportDeviceRequest.setDt(date);
            reportDeviceRequest.setDevice_status_id(1);
            reportDeviceRequest.setDevice_status(StatusCodeEnum.STATUS_UP.name);
            reportDeviceRequest.setDevice_type_id(10);
            reportDeviceRequest.setDevice_type(DeviceTypeCodeEnum.DEVICE_TYPE_KEY_SWITCH.toString());
            reportDeviceRequest.setEvent_type_id(11);
            reportDeviceRequest.setEvent_type(EventTypeCodeEnum.EVENT_TYPE_KEY_SWITCH_UNLOCKED.name);

            reportDeviceRequest2.setSerial("");
            reportDeviceRequest2.setCabinet_code(cabinet.getCode());
            reportDeviceRequest2.setDt(date);
            reportDeviceRequest2.setDevice_status_id(1);
            reportDeviceRequest2.setDevice_status(StatusCodeEnum.STATUS_UP.name);
            reportDeviceRequest2.setDevice_type_id(1);
            reportDeviceRequest2.setDevice_type(DeviceTypeCodeEnum.DEVICE_TYPE_SMART_LOCK.name);
            reportDeviceRequest2.setEvent_type_id(2);
            reportDeviceRequest2.setEvent_type(EventTypeCodeEnum.EVENT_TYPE_SMART_LOCK_UNLOCKED.name);

            List<ReportDeviceRequest> devices = new ArrayList<>();
            devices.add(reportDeviceRequest);
            devices.add(reportDeviceRequest2);
            statusReportRequest.setDevices(devices);
            sswsService.forceUpdateCabinetStatus(statusReportRequest);

            log.info("before resposnse success");
            ActivityLog activityLogSuccess = new ActivityLog("unlockCabinet", "UNLOCK", callResult.get("errMsg"),
                    "INFO", "Success");
//            activityLogRepository.save(activityLogSuccess);

            return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse(true, "UPDATE OK"));
        } else {
            // Todo: error msg
            log.info("before response fail");
            ActivityLog activityLogFail = new ActivityLog("unlockCabinet", "UNLOCK", callResult.get("errMsg"),
                    "INFO", "Fail");
//            activityLogRepository.save(activityLogFail);

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse(false, "UPDATE FAILED"));
        }
    }

    @Transactional
    public ResponseEntity<CabinetStatusResponses> getCabinetStatus(SiteStatusRequest request) {
        log.info("AIS call getCabinetStatus"+request.getSite_code());

        CabinetStatusResponses responses = new CabinetStatusResponses();

        ActivityLog activityLogAuthorized = new ActivityLog(
                "getCabinetStatus",
                "SITE",
                "Get Cabinet Status",
                "INFO",
                "Authorized");
//        activityLogRepository.save(activityLogAuthorized);

        ActivityLog activityLogInit = new ActivityLog(
                "getCabinetStatus",
                "SITE",
                "Get Cabinet Status " + request.getSite_code(),
                "INFO",
                "Init");
//        activityLogRepository.save(activityLogInit);

        // Site
        Site site = siteRepository.findByCode(request.getSite_code()).orElse(null);

        if (site == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(responses);
        }

        // Cabinet
        List<Long> cabinetIds = new ArrayList<>();

        cabinetRepository.findBySite(site).forEach((cabinet) -> {
            cabinetIds.add(cabinet.getId());
        });
        List<Cabinet> cabinets = cabinetRepository.findByIdInAndActiveIsTrue(cabinetIds);

        List<CabinetStatusResponse> response_list = cabinets.stream().map(cabinet -> {
            CabinetStatusResponse response = new CabinetStatusResponse();
            response.setSite_code(cabinet.getSite().getCode());
            response.setCabinet_code(cabinet.getCode());
            if (cabinet.getLockEventCode().equals(LockEventCodeEnum.LOCK_EVENT_LOCK)) {
                response.setLocked("True");
            } else {
                response.setLocked("False");
            }

            // Todo: is_emergency
            if (site.getStatus().code.equals(StatusCodeEnum.STATUS_UP.code)) {
                response.setIs_emergency("No");
            } else {
                response.setIs_emergency("Yes");
            }

            response.setLast_updated(cabinet.getUpdatedAt());
            response.setEmerg_key(cabinet.getSite().getSite_key());
            response.setMac_address(cabinet.getMac_address());
            response.setDevice_name("SmartSite_" + cabinet.getCode());

            return response;
        }).collect(Collectors.toList());

        responses.setCabinets(response_list);

        ActivityLog activityLogSuccess = new ActivityLog(
                "getCabinetStatus",
                "SITE",
                "Get Cabinet Status " + request.getSite_code(),
                "INFO",
                "Success");
//        activityLogRepository.save(activityLogSuccess);
        return ResponseEntity.status(HttpStatus.OK).body(responses);
    }

    private Map<String, String> callAuxRealDevice(String ip, int port, Cabinet cabinet, boolean is_on) {
        String targetUrl = "http://"+ip+":"+port+AppConstants.AUX_ON_URL;

        if (is_on) {
            targetUrl = "http://"+ip+":"+port+AppConstants.AUX_ON_URL;
        } else {
            targetUrl = "http://"+ip+":"+port+AppConstants.AUX_OFF_URL;
        }

        log.info("call Auxiliary API on Real Device "+targetUrl+" "+cabinet.getCode());

        HashMap<String, String> result = new HashMap<>();
        String errMsg = "";
        boolean isError = false;
        String apikey_for_device = env.getProperty("apikey_for_device");
        try {
            HttpResponse<String> response = Unirest.post(targetUrl)
                    .header("Content-Type", "application/json")
                    .header("apiKey", apikey_for_device)
                    .body("{\"cabinet_code\": \"" + cabinet.getCode() + "\"}")
                    .asString();

            // Retrieve the parsed JSONObject from the response
            String responseString = response.getBody();
            JSONObject jsonResp = new JSONObject(responseString);
            log.info("responseString: "+responseString);
            boolean is_success = (boolean) jsonResp.get("is_success");
            if (is_success == true) {
                isError = false;
//                cabinet.setAuxout(is_on);
//                cabinetRepository.save(cabinet);
                cabinet.getAux_devices().forEach((aux) -> {
                    if (aux.getAuxNo() == 0) {
                        aux.setIs_on(is_on);
                        log.info("update aux output to db: "+is_on);
                    }
                });
                cabinetRepository.save(cabinet);
            } else {
                isError = true;
            }
            errMsg = String.valueOf( jsonResp.get("err_msg") );
            log.info("msg "+errMsg);
        } catch (UnirestException e) {
            e.printStackTrace();
            isError = true;
            errMsg = "Failed to communicate with targeted site.\n" + ip+":"+port;
            log.info("errMsg "+errMsg);
        } catch (org.json.JSONException e) {
            e.printStackTrace();
            isError = true;
            errMsg = "Aux out parse json err site.\n" + ip+":"+port;
            log.info("errMsg "+errMsg);
        }

        result.put("isError", ""+isError);
        result.put("errMsg", errMsg);
        log.info("return result: "+result);
        return result;
    }

    public ResponseEntity<ApiResponse> auxOnOff(CabinetLockRequest cabinetRequest, boolean is_on) {
        log.info("auxOn " + cabinetRequest.getCabinet_code());
        Cabinet cabinet = cabinetRepository.findByCode(cabinetRequest.getCabinet_code());
        if (cabinet == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(true, "No cabinet"));
        }
        String ip = cabinet.getSite().getIp_address();
        int port = cabinet.getSite().getPort();

        Map<String, String> callResult = callAuxRealDevice(ip, port, cabinet, is_on);

        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse(true, "OK"));
    }

    public SiteVersion getSiteVersion(String site_code) {
        log.info("GET Site Version of " +  site_code);
        Site site = siteRepository.findByCode(site_code).orElse(null);

        SiteVersion siteVersion = null;
        if(site != null){
            siteVersion = siteVersionRepository.findFirstBySiteId(site.getId()).orElse(null);
        }

        return siteVersion;
    }

    @Cacheable(value = "cabinetCache")
    public List<Cabinet> getListOfcabinetCaches() {
        System.out.println("getListOfcabinetCaches is running...");

        List<Cabinet> cabinets = this.cabinetRepository.findAll();

        return cabinets;
    }
}
