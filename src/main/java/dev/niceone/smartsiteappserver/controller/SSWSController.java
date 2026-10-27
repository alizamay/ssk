package dev.niceone.smartsiteappserver.controller;

import dev.niceone.smartsiteappserver.payload.ApiResponse;
import dev.niceone.smartsiteappserver.payload.IOReport;
import dev.niceone.smartsiteappserver.payload.request.OldImgUploadRequest;
import dev.niceone.smartsiteappserver.payload.request.StatusReportRequest;
import dev.niceone.smartsiteappserver.service.SSWSService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/device")
@Slf4j
public class SSWSController {
    @Autowired
    private Environment env;

    @Autowired
    private SSWSService sswsService;

    @PostMapping(value = "/status/report2")
    ResponseEntity<?> statusReport2(@Valid @RequestBody StatusReportRequest request,
                                    @RequestHeader HttpHeaders headers) {
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

        sswsService.updateCabinetStatus(request);
        log.info("v2 api status report from: "+request.getSite_code());
        return ResponseEntity.ok("OK");
    }

    @PostMapping(value = "/status/report")
    ResponseEntity<?> statusReport(@Valid @RequestBody StatusReportRequest request,
                                   @RequestHeader HttpHeaders headers) {
        
        // Fast API key validation (no exception handling overhead)
        List<String> apikey = headers.get("apikey");
        if (apikey == null || apikey.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse(false, "no apikey"));
        }
        
        try {
            if (!checkApiKey(apikey.get(0))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse(false, "Invalid API key"));
            }
        } catch (IOException e) {
            log.warn("API key validation error for {}: {}", request.getSite_code(), e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse(false, "no apikey"));
        }

        // The debouncer path (markSiteUp) already updates status/reportStatusAt per site.
        // HighThroughputStatusService was removed: its worker threads deadlocked on a
        // read->write lock upgrade and its unbounded queue retained every report until OOM.
        return sswsService.updateCabinetStatus_old(request);
    }

    @PostMapping(value = "/status/report_test")
    ResponseEntity<?> statusReportTest(@Valid @RequestBody StatusReportRequest request,
                                   @RequestHeader HttpHeaders headers) {
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

        return ResponseEntity.ok("OK");
    }

    @PostMapping(value = "/img/upload")
    ResponseEntity<?> oldImgUpload(@Valid @RequestBody OldImgUploadRequest request,
                                   @RequestHeader HttpHeaders headers) {
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

//        log.info("status report from: "+request.getSite_code());

        return ResponseEntity.status(HttpStatus.OK).body(sswsService.oldImgUpload(request));
    }

    @PostMapping(value = "/status/io_report")
    ResponseEntity<?> ioReport(@Valid @RequestBody IOReport request,
                                   @RequestHeader HttpHeaders headers) {
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

        return sswsService.ioReport(request);
    }

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
}
