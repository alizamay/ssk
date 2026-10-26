package dev.niceone.smartsiteappserver.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import dev.niceone.smartsiteappserver.mapper.AlarmLogModelMapper;
import dev.niceone.smartsiteappserver.model.*;
import dev.niceone.smartsiteappserver.payload.*;
import dev.niceone.smartsiteappserver.payload.request.AlarmCSVRequest;
import dev.niceone.smartsiteappserver.payload.request.PiAlarmLogRequest;
import dev.niceone.smartsiteappserver.payload.request.SiteCSVRequest;
import dev.niceone.smartsiteappserver.payload.request.SiteStatusRequest;
import dev.niceone.smartsiteappserver.payload.response.*;
import dev.niceone.smartsiteappserver.security.CurrentUser;
import dev.niceone.smartsiteappserver.security.UserPrincipal;
import dev.niceone.smartsiteappserver.service.AlarmLogCasService;
import dev.niceone.smartsiteappserver.service.AlarmLogService;
import dev.niceone.smartsiteappserver.service.OnsiteCheckLogService;
import dev.niceone.smartsiteappserver.service.SchedulerConfigService;
import dev.niceone.smartsiteappserver.specification.AlarmLogSpecification;
import dev.niceone.smartsiteappserver.specification.SearchCriteria;
import dev.niceone.smartsiteappserver.specification.SearchOperation;
import dev.niceone.smartsiteappserver.specification.SpecSearchCriteria;
import lombok.extern.slf4j.Slf4j;
import net.kaczmarzyk.spring.data.jpa.domain.*;
import javax.servlet.http.HttpServletRequest;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Conjunction;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Or;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Spec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Spec(path="resolveDate", params = "have_resolve_date", spec= NotNull.class)
interface NotNullClearedDateSpec extends Specification<AlarmLog> {
}

@Spec(path="resolveDate", params = "no_resolve_date", spec= Null.class)
interface NullClearedDateSpec extends Specification<AlarmLog> {
}

@Spec(path="createdAt", params = "start_created_at", spec= GreaterThanOrEqual.class)
interface StartCreatedDateSpec extends Specification<AlarmLog> {
}

@Conjunction(value = {
        @Or({@Spec(path = "site.name_th", params = "keyword", spec = LikeIgnoreCase.class),
                @Spec(path = "site.code", params = "keyword", spec = LikeIgnoreCase.class)}),
})
interface KeywordSpec extends Specification<AlarmLog> {
}

@Spec(path="createdAt", params = "end_created_at", spec= LessThanOrEqual.class)
interface EndCreatedDateSpec extends Specification<AlarmLog> {
}

@Spec(path="active", params = "active", spec=Equal.class)
interface IsActiveSpec extends Specification<AlarmLog> {
}

@RestController
@Slf4j
@RequestMapping("/api")
public class AlarmLogController {
    @Autowired
    private Environment env;

    @Autowired
    private AlarmLogService alarmLogService;

    @Autowired
    private AlarmLogCasService alarmLogCasService;

    @Autowired
    private SchedulerConfigService schedulerConfigService;

    @Autowired
    private OnsiteCheckLogService onsiteCheckLogService;

    // Get
    @GetMapping("/event/list")
    PagedResponse<AlarmLogResponse> findAlarm(
        @RequestParam(name="have_resolve_date", required=false) Boolean have_resolve_date,
        @RequestParam(name="no_resolve_date", required=false) Boolean no_resolve_date,
        @RequestParam(name="start_created_at", required=false) String start_createdAt,
        @RequestParam(name="end_created_at", required=false) String end_createdAt,
        @RequestParam(name="keyword", required=false) String keyword,
        @RequestParam(name="active", required=false) Boolean active,
        @RequestParam(value = "pageNumber", defaultValue = "0") int pageNumber,
        @RequestParam(value = "pageSize", defaultValue = "30") int pageSize,
        @CurrentUser UserPrincipal currentUser,
        NotNullClearedDateSpec notNullClearedDateSpec,
        NullClearedDateSpec nullClearedDateSpec,
        StartCreatedDateSpec startCreatedDateSpec,
        EndCreatedDateSpec endCreatedDateSpec,
        KeywordSpec keywordSpec,
        IsActiveSpec isActiveSpec) {
        return alarmLogCasService.getEventList(
                notNullClearedDateSpec
                        .and(nullClearedDateSpec)
                        .and(startCreatedDateSpec)
                        .and(endCreatedDateSpec)
                        .and(keywordSpec)
                        .and(isActiveSpec), pageNumber, pageSize, currentUser,start_createdAt,end_createdAt,keyword);
    }


    @GetMapping("/activity/list")
    PagedResponse<AlarmLogResponse> findActivity(
            @RequestParam(name="have_resolve_date", required=false) Boolean have_resolve_date,
            @RequestParam(name="no_resolve_date", required=false) Boolean no_resolve_date,
            @RequestParam(name="start_created_at", required=false) String start_createdAt,
            @RequestParam(name="end_created_at", required=false) String end_createdAt,
            @RequestParam(name="keyword", required=false) String keyword,
            @RequestParam(name="active", required=false) Boolean active,
            @RequestParam(value = "pageNumber", defaultValue = "0") int pageNumber,
            @RequestParam(value = "pageSize", defaultValue = "30") int pageSize,
            @CurrentUser UserPrincipal currentUser,
            NotNullClearedDateSpec notNullClearedDateSpec,
            NullClearedDateSpec nullClearedDateSpec,
            StartCreatedDateSpec startCreatedDateSpec,
            EndCreatedDateSpec endCreatedDateSpec,
            KeywordSpec keywordSpec,
            IsActiveSpec isActiveSpec) {
        return alarmLogCasService.getActivityList(
                notNullClearedDateSpec
                        .and(nullClearedDateSpec)
                        .and(startCreatedDateSpec)
                        .and(endCreatedDateSpec)
                        .and(keywordSpec)
                        .and(isActiveSpec), pageNumber, pageSize, currentUser,start_createdAt,end_createdAt,keyword);
    }


    // Get
    @GetMapping("/alarm/list")
    PagedResponse<AlarmLogResponse> findAlarmEvent(
            @RequestParam(name="have_resolve_date", required=false) Boolean have_resolve_date,
            @RequestParam(name="no_resolve_date", required=false) Boolean no_resolve_date,
            @RequestParam(name="start_created_at", required=false) String start_createdAt,
            @RequestParam(name="end_created_at", required=false) String end_createdAt,
            @RequestParam(name="keyword", required=false) String keyword,
            @RequestParam(name="active", required=false) Boolean active,
            @RequestParam(value = "pageNumber", defaultValue = "0") int pageNumber,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @CurrentUser UserPrincipal currentUser,
            NotNullClearedDateSpec notNullClearedDateSpec,
            NullClearedDateSpec nullClearedDateSpec,
            StartCreatedDateSpec startCreatedDateSpec,
            EndCreatedDateSpec endCreatedDateSpec,
            KeywordSpec keywordSpec,
            IsActiveSpec isActiveSpec) {
        return alarmLogCasService.getAlarmList(
                notNullClearedDateSpec
                        .and(nullClearedDateSpec)
                        .and(startCreatedDateSpec)
                        .and(endCreatedDateSpec)
                        .and(keywordSpec)
                        .and(isActiveSpec), pageNumber, pageSize, currentUser,start_createdAt,end_createdAt,keyword);
    }




    // Pi update or create
    @PostMapping("/alarm/update/")
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<?> piAlarm(@Valid @RequestBody PiAlarmLogRequest alarmLogRequest, @RequestHeader HttpHeaders headers) throws JsonProcessingException {
        List<String> apikey = headers.get("apikey");
        try {
            if (apikey != null) {
                if(!checkApiKey(apikey.get(0))) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse(false, "Invalid API key"));
                }
            } else {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse(false, "no apikey"));
            }
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse(false, "no apikey"));
        }

        ResponseEntity<ApiResponse> response = alarmLogCasService.piAlarm(alarmLogRequest);
        return response;
    }

    // Pi ask on site
    @PostMapping("/check/onsite/")
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<?> askTTSM(@Valid @RequestBody SiteStatusRequest siteStatusRequest,
                               @RequestHeader HttpHeaders headers,
                               HttpServletRequest request) {
        List<String> apikey = headers.get("apikey");
        try {
            if (apikey != null) {
                if(!checkApiKey(apikey.get(0))) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse(false, "Invalid API key"));
                }
            } else {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse(false, "no apikey"));
            }
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse(false, "no apikey"));
        }

        String onsiteSource = schedulerConfigService.getConfigValue("onsite.source", "db");
        log.info("askTTSM | onsite.source={}", onsiteSource);

        ResponseEntity<OnsiteResponse> response;
        if ("ttsm_api".equals(onsiteSource)) {
            response = alarmLogService.askTTSMOnSite(siteStatusRequest);
        } else {
            response = alarmLogCasService.askTTSMOnSite(siteStatusRequest);
        }

        if (response.getBody() != null) {
            String clientIp = getClientIp(request);
            OnsiteResponse body = response.getBody();
            onsiteCheckLogService.save(siteStatusRequest.getSite_code(), clientIp,
                    body.getStatus(), body.getRaw());
        }

        return response;
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

//    // Create
//    @PostMapping("/alarm/log/")
//    @PreAuthorize("hasRole('ADMIN')")
//    @ResponseStatus(HttpStatus.CREATED)
//    ResponseEntity<?> createAlarm(@Valid @RequestBody AlarmLogRequest alarmLogRequest) {
//        ResponseEntity<ApiResponse> response = alarmLogService.createAlarm(alarmLogRequest);
//        return response;
//    }
//
//    // Update
//    @PutMapping("/resolve-alarm/{id}")
//    @PreAuthorize("hasRole('ADMIN')")
//    ResponseEntity<?> updateResolveAlarm(@Valid @PathVariable Long id) {
//        return alarmLogService.updateResolveAlarm(id);
//    }

    private boolean checkApiKey(String input_apiKey) throws IOException {
        boolean isValidKey = false;
        // Get api key from .properties

        String apikey_for_device = env.getProperty("apikey_for_device");

        // Compare keys
        if(Objects.equals(input_apiKey, apikey_for_device)) {
            isValidKey = true;
        }
        return isValidKey;
    }

    @PostMapping(value = "/csv/event")
    public ResponseEntity<byte[]> generateCsvEventFile(@RequestBody AlarmCSVRequest req){


        StringBuilder csvContent = new StringBuilder();
        csvContent.append('\uFEFF');
        csvContent.append("Site Code,Site NameTh,Site NameEn,Region Code,Region Name,Created Date,Created Time,Update Date,Update Time,Alarm Type Code,Alarm Type Name,Cabinet No,Cabinet Code,Lock Event Name,Door EventName,Count Create Event,Count Resolve Event\n");

        LocalDateTime startDateTime = LocalDateTime.now().with(LocalTime.of(0, 0, 1));
        LocalDateTime endDateTime = LocalDateTime.now().with(LocalTime.of(23, 59, 59));



        if( req.getStart_created_at() != null && !req.getStart_created_at().isEmpty() && req.getEnd_created_at() != null && !req.getEnd_created_at().isEmpty()){
            try {
                // Parse the string to LocalDateTime
                 startDateTime = (LocalDateTime.parse(req.getStart_created_at())).plusHours(7);
                 endDateTime = (LocalDateTime.parse(req.getEnd_created_at())).plusHours(7);

            }catch (Exception ex){

            }
        }

        String startDateTimeStr = startDateTime.format(DateTimeFormatter.ISO_DATE_TIME);
        String endDateTimeStr = endDateTime.format(DateTimeFormatter.ISO_DATE_TIME);

        List<SiteEventLogWithCount>  retLog = alarmLogCasService.getResolveDateIsNullAllLogs(startDateTime,endDateTime,req.getKeyword());

        DateTimeFormatter formatterDate = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        DateTimeFormatter formatterTime = DateTimeFormatter.ofPattern("HH:mm:ss");


        retLog.forEach(alarm -> {

            try {
                String cDateStr = alarm.getCreateDate().format(formatterDate); // "1986-04-08 12:30"
                String cTimeStr = alarm.getCreateDate().format(formatterTime); // "1986-04-08 12:30"
                String rDateStr = alarm.getUpdateDate().format(formatterDate); // "1986-04-08 12:30"
                String rTimeStr = alarm.getUpdateDate().format(formatterTime); // "1986-04-08 12:30"



                csvContent.append(alarm.getSiteCode() != null ? alarm.getSiteCode() : "").append(",")
                        .append(alarm.getSiteNameTh() != null ? alarm.getSiteNameTh() : "").append(",")
                        .append(alarm.getSiteNameEn() != null ? alarm.getSiteNameEn() : "").append(",")
                        .append(alarm.getRegionCode() != null ? alarm.getRegionCode() : "").append(",")
                        .append(alarm.getRegionName() != null ? alarm.getRegionName() : "").append(",")
                        .append(cDateStr != null ? cDateStr : "").append(",")
                        .append(cTimeStr != null ? cTimeStr : "").append(",")
                        .append(rDateStr != null ? rDateStr : "").append(",")
                        .append(rTimeStr != null ? rTimeStr : "").append(",")
                        .append(alarm.getAlarmTypeCode() != null ? alarm.getAlarmTypeCode() : "").append(",")
                        .append(alarm.getAlarmTypeName() != null ? alarm.getAlarmTypeName() : "").append(",")
                        .append(alarm.getCabinetNo() != null ? alarm.getCabinetNo() : "").append(",")
                        .append(alarm.getCabinetCode() != null ? alarm.getCabinetCode() : "").append(",")
                        .append(alarm.getLockEventName() != null ? alarm.getLockEventName() : "").append(",")
                        .append(alarm.getDoorEventName() != null ? alarm.getDoorEventName() : "").append(",")
                        .append(alarm.getCountCreateEvent() != null ? alarm.getCountCreateEvent() : 0).append(",")
                        .append(alarm.getCountResolveEvent() != null ? alarm.getCountResolveEvent() : 0).append(",")
                        .append("\n");

            }catch (Exception ex){

            }


        });



        HttpHeaders headers = new HttpHeaders();
        // headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.set(HttpHeaders.CONTENT_TYPE, "application/octet-stream; charset=UTF-8");
        headers.setContentDispositionFormData("attachment", "eventLogs.csv");

        byte[] csvBytes = csvContent.toString().getBytes();

        return new ResponseEntity<>(csvBytes, headers, HttpStatus.OK);
    }

    @PostMapping(value = "/csv/activity")
    public ResponseEntity<byte[]> generateCsvActivityFile(@RequestBody AlarmCSVRequest req){


        StringBuilder csvContent = new StringBuilder();
        csvContent.append('\uFEFF');
        csvContent.append("Site Code,Site NameTh,Site NameEn,Region Code,Region Name,Alarm Type Code,Alarm Type Name,Cabinet No,Cabinet Code,Lock Event Name,Door EventName,Created Date,Created Time,Resolve Date,Resolve Time\n");

        LocalDateTime startDateTime = LocalDateTime.now().with(LocalTime.of(0, 0, 1));
        LocalDateTime endDateTime = LocalDateTime.now().with(LocalTime.of(23, 59, 59));
        List<Integer> alarmTypes = Arrays.asList(9,1,2);

        if( req.getStart_created_at() != null && !req.getStart_created_at().isEmpty() && req.getEnd_created_at() != null && !req.getEnd_created_at().isEmpty()){
            try {
                // Parse the string to LocalDateTime
                startDateTime = (LocalDateTime.parse(req.getStart_created_at())).plusHours(7);
                endDateTime = (LocalDateTime.parse(req.getEnd_created_at())).plusHours(7);

            }catch (Exception ex){

            }
        }

        List<SiteEventLogWithResolve>  retLog = alarmLogCasService.getResolveDateAllLogs(startDateTime,endDateTime,req.getKeyword(),alarmTypes);


        DateTimeFormatter formatterDate = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        DateTimeFormatter formatterTime = DateTimeFormatter.ofPattern("HH:mm:ss");


        retLog.forEach(alarm -> {

            try {

                String cDateStr = alarm.getCreateDate().format(formatterDate); // "1986-04-08 12:30"
                String cTimeStr = alarm.getCreateDate().format(formatterTime); // "1986-04-08 12:30"
                String rDateStr = alarm.getResolveDate().format(formatterDate); // "1986-04-08 12:30"
                String rTimeStr = alarm.getResolveDate().format(formatterTime); // "1986-04-08 12:30"

                csvContent.append(alarm.getSiteCode() != null ? alarm.getSiteCode() : "").append(",")
                        .append(alarm.getSiteNameTh() != null ? alarm.getSiteNameTh() : "").append(",")
                        .append(alarm.getSiteNameEn() != null ? alarm.getSiteNameEn() : "").append(",")
                        .append(alarm.getRegionCode() != null ? alarm.getRegionCode() : "").append(",")
                        .append(alarm.getRegionName() != null ? alarm.getRegionName() : "").append(",")
                        .append(alarm.getAlarmTypeCode() != null ? alarm.getAlarmTypeCode() : "").append(",")
                        .append(alarm.getAlarmTypeName() != null ? alarm.getAlarmTypeName() : "").append(",")
                        .append(alarm.getCabinetNo() != null ? alarm.getCabinetNo() : "").append(",")
                        .append(alarm.getCabinetCode() != null ? alarm.getCabinetCode() : "").append(",")
                        .append(alarm.getLockEventName() != null ? alarm.getLockEventName() : "").append(",")
                        .append(alarm.getDoorEventName() != null ? alarm.getDoorEventName() : "").append(",")
                        .append(cDateStr != null ? cDateStr : "").append(",")
                        .append(cTimeStr != null ? cTimeStr : "").append(",")
                        .append(rDateStr != null ? rDateStr : "").append(",")
                        .append(rTimeStr != null ? rTimeStr : "").append(",")
                        .append("\n");

            }catch (Exception e){

            }

        });



        HttpHeaders headers = new HttpHeaders();
        // headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.set(HttpHeaders.CONTENT_TYPE, "application/octet-stream; charset=UTF-8");
        headers.setContentDispositionFormData("attachment", "siteInfo.csv");

        byte[] csvBytes = csvContent.toString().getBytes();

        return new ResponseEntity<>(csvBytes, headers, HttpStatus.OK);
    }

    @PostMapping(value = "/csv/alarm")
    public ResponseEntity<byte[]> generateCsvAlarmFile(@RequestBody AlarmCSVRequest req){


        StringBuilder csvContent = new StringBuilder();
        csvContent.append('\uFEFF');
        csvContent.append("Site Code,Site NameTh,Site NameEn,Region Code,Region Name,Alarm Type Code,Alarm Type Name,Cabinet No,Cabinet Code,Lock Event Name,Door EventName,Created Date,Created Time,Resolve Date,Resolve Time\n");


        LocalDateTime startDateTime = LocalDateTime.now().with(LocalTime.of(0, 0, 1));
        LocalDateTime endDateTime = LocalDateTime.now().with(LocalTime.of(23, 59, 59));
        List<Integer> alarmTypes = Arrays.asList(15, 16, 17, 18);

        if( req.getStart_created_at() != null && !req.getStart_created_at().isEmpty() && req.getEnd_created_at() != null && !req.getEnd_created_at().isEmpty()){
            try {
                // Parse the string to LocalDateTime
                startDateTime = (LocalDateTime.parse(req.getStart_created_at())).plusHours(7);
                endDateTime = (LocalDateTime.parse(req.getEnd_created_at())).plusHours(7);

            }catch (Exception ex){

            }
        }

        List<SiteEventLogWithResolve>  retLog = alarmLogCasService.getResolveDateAllLogs(startDateTime,endDateTime,req.getKeyword(),alarmTypes);


        DateTimeFormatter formatterDate = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        DateTimeFormatter formatterTime = DateTimeFormatter.ofPattern("HH:mm:ss");


        retLog.forEach(alarm -> {

            try {

                String cDateStr = alarm.getCreateDate().format(formatterDate); // "1986-04-08 12:30"
                String cTimeStr = alarm.getCreateDate().format(formatterTime); // "1986-04-08 12:30"
                String rDateStr = alarm.getResolveDate().format(formatterDate); // "1986-04-08 12:30"
                String rTimeStr = alarm.getResolveDate().format(formatterTime); // "1986-04-08 12:30"

                csvContent.append(alarm.getSiteCode() != null ? alarm.getSiteCode() : "").append(",")
                        .append(alarm.getSiteNameTh() != null ? alarm.getSiteNameTh() : "").append(",")
                        .append(alarm.getSiteNameEn() != null ? alarm.getSiteNameEn() : "").append(",")
                        .append(alarm.getRegionCode() != null ? alarm.getRegionCode() : "").append(",")
                        .append(alarm.getRegionName() != null ? alarm.getRegionName() : "").append(",")
                        .append(alarm.getAlarmTypeCode() != null ? alarm.getAlarmTypeCode() : "").append(",")
                        .append(alarm.getAlarmTypeName() != null ? alarm.getAlarmTypeName() : "").append(",")
                        .append(alarm.getCabinetNo() != null ? alarm.getCabinetNo() : "").append(",")
                        .append(alarm.getCabinetCode() != null ? alarm.getCabinetCode() : "").append(",")
                        .append(alarm.getLockEventName() != null ? alarm.getLockEventName() : "").append(",")
                        .append(alarm.getDoorEventName() != null ? alarm.getDoorEventName() : "").append(",")
                        .append(cDateStr != null ? cDateStr : "").append(",")
                        .append(cTimeStr != null ? cTimeStr : "").append(",")
                        .append(rDateStr != null ? rDateStr : "").append(",")
                        .append(rTimeStr != null ? rTimeStr : "").append(",")
                        .append("\n");

            }catch (Exception e){

            }

        });


        HttpHeaders headers = new HttpHeaders();
        // headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.set(HttpHeaders.CONTENT_TYPE, "application/octet-stream; charset=UTF-8");
        headers.setContentDispositionFormData("attachment", "siteInfo.csv");

        byte[] csvBytes = csvContent.toString().getBytes();

        return new ResponseEntity<>(csvBytes, headers, HttpStatus.OK);
    }

}
