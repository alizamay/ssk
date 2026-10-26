package dev.niceone.smartsiteappserver.controller;

import dev.niceone.smartsiteappserver.payload.ApiResponse;
import dev.niceone.smartsiteappserver.service.SchedulerConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/config/onsite-source")
public class OnsiteSourceController {

    private static final String CONFIG_KEY = "onsite.source";
    private static final List<String> VALID_SOURCES = Arrays.asList("db", "ttsm_api");

    @Autowired
    private SchedulerConfigService schedulerConfigService;

    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        String current = schedulerConfigService.getConfigValue(CONFIG_KEY, "db");
        Map<String, Object> response = new HashMap<>();
        response.put("current", current);
        response.put("available", VALID_SOURCES);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/switch")
    public ResponseEntity<?> switchSource(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String source = body.get("source");

        if (source == null || !VALID_SOURCES.contains(source)) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Invalid source. Must be one of: " + VALID_SOURCES));
        }

        String updatedBy = getClientInfo(request);
        boolean success = schedulerConfigService.updateConfig(CONFIG_KEY, source, updatedBy);

        if (!success) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Failed to update config"));
        }

        log.info("onsite.source switched to: {} by: {}", source, updatedBy);

        Map<String, Object> response = new HashMap<>();
        response.put("current", source);
        response.put("message", "Switched successfully");
        return ResponseEntity.ok(response);
    }

    private String getClientInfo(HttpServletRequest request) {
        String clientIp = request.getHeader("X-Forwarded-For");
        if (clientIp == null || clientIp.isEmpty()) {
            clientIp = request.getRemoteAddr();
        }
        return "API-" + clientIp;
    }
}
