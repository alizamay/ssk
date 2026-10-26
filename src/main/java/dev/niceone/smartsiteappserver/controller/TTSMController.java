package dev.niceone.smartsiteappserver.controller;

import dev.niceone.smartsiteappserver.payload.ApiResponse;
import dev.niceone.smartsiteappserver.payload.CabinetStatusResponse;
import dev.niceone.smartsiteappserver.payload.request.CabinetLockRequest;
import dev.niceone.smartsiteappserver.payload.request.SiteStatusRequest;
import dev.niceone.smartsiteappserver.service.CabinetService;
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
@RequestMapping("/ttsm/cabinet")
@Slf4j
public class TTSMController {

    @Autowired
    private Environment env;

    @Autowired
    private CabinetService cabinetService;

    @PostMapping(value = "/lock")
    ResponseEntity<?> ttsmRequestKeyLock(@Valid @RequestBody CabinetLockRequest cabinetRequest,
                                          @RequestHeader HttpHeaders headers) {
        //Check AIS api key
        List<String> apikey = headers.get("apikey");
        try {
            if (apikey != null) {
                log.info(apikey.get(0));
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

        return cabinetService.keyLock(cabinetRequest);
    }

    @PostMapping(value = "/unlock")
    ResponseEntity<?> ttsmRequestKeyUnLock(@Valid @RequestBody CabinetLockRequest cabinetRequest,
                                            @RequestHeader HttpHeaders headers) {
        //Check AIS api key
        List<String> apikey = headers.get("apikey");
        try {
            if (apikey != null) {
                log.info(apikey.get(0));
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

        return cabinetService.keyUnlock(cabinetRequest);
    }

    @PostMapping("/status")
    @ResponseStatus(HttpStatus.OK)
    ResponseEntity<?> ttsmRequestcabinetStatus(@Valid @RequestBody SiteStatusRequest request,
                                    @RequestHeader HttpHeaders headers) {
        //Check AIS api key
        List<String> apikey = headers.get("apikey");
        try {
            if (apikey != null) {
                log.info(apikey.get(0));
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

        return cabinetService.getCabinetStatus(request);
    }

    private boolean checkApiKey(String input_apiKey) throws IOException {
        boolean isValidKey = false;
        // Get api key from .properties
        String apikey_for_ttsm = env.getProperty("apikey_for_ttsm");

        // Compare keys
        if(Objects.equals(input_apiKey, apikey_for_ttsm)) {
            isValidKey = true;
        }
        return isValidKey;
    }
}
