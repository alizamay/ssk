package dev.niceone.smartsiteappserver.controller;

import dev.niceone.smartsiteappserver.scheduler.DailyReportScheduler;
import dev.niceone.smartsiteappserver.scheduler.SiteScheduler;
import dev.niceone.smartsiteappserver.service.SchedulerConfigService;
import dev.niceone.smartsiteappserver.payload.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/scheduler")
public class SchedulerController {

    @Autowired
    private SiteScheduler siteScheduler;

    @Autowired
    private DailyReportScheduler dailyReportScheduler;
    
    @Autowired
    private ConfigurableEnvironment env;
    
    @Autowired
    private SchedulerConfigService schedulerConfigService;

    @GetMapping("/cron")
    public ResponseEntity<Map<String, String>> getAllCronExpressions() {
        return ResponseEntity.ok(siteScheduler.getAllCronExpressions());
    }

    @GetMapping("/cron/{taskName}")
    public ResponseEntity<String> getCronExpression(@PathVariable String taskName) {
        String cronExpression = siteScheduler.getCronExpression(taskName);
        if (cronExpression != null) {
            return ResponseEntity.ok(cronExpression);
        }
        return ResponseEntity.notFound().build();
    }

    @PutMapping("/cron/{taskName}")
    public ResponseEntity<String> updateCronExpression(
            @PathVariable String taskName, 
            @RequestBody Map<String, String> payload) {
        
        String cronExpression = payload.get("cronExpression");
        if (cronExpression == null || cronExpression.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("cronExpression is required");
        }

        try {
            // Validate cron expression format (basic validation)
            String[] parts = cronExpression.trim().split("\\s+");
            if (parts.length != 6) {
                return ResponseEntity.badRequest().body("Invalid cron expression format. Expected 6 parts (second minute hour day month dayOfWeek)");
            }

            siteScheduler.updateCronExpression(taskName, cronExpression);
            log.info("Updated cron expression for task: {} to: {}", taskName, cronExpression);
            
            return ResponseEntity.ok("Cron expression updated successfully for task: " + taskName);
        } catch (Exception e) {
            log.error("Error updating cron expression for task: {}", taskName, e);
            return ResponseEntity.badRequest().body("Error updating cron expression: " + e.getMessage());
        }
    }

    @PostMapping("/trigger/{taskName}")
    public ResponseEntity<String> triggerTask(@PathVariable String taskName) {
        try {
            switch (taskName.toLowerCase()) {
                case "morning_status":
                case "insertmorningstatus":
                    dailyReportScheduler.insertMorningStatus();
                    return ResponseEntity.ok("Morning status task triggered successfully");

                case "offline_check":
                case "checkprovisoffline":
                    dailyReportScheduler.checkProveIsOffline();
                    return ResponseEntity.ok("Offline check task triggered successfully");

                case "online_check":
                case "checkprovisonline":
                    dailyReportScheduler.checkProveIsOnline();
                    return ResponseEntity.ok("Online check task triggered successfully");
                
                default:
                    return ResponseEntity.badRequest().body("Unknown task: " + taskName);
            }
        } catch (Exception e) {
            log.error("Error triggering task: {}", taskName, e);
            return ResponseEntity.badRequest().body("Error triggering task: " + e.getMessage());
        }
    }
    
    /**
     * Get current scheduler configuration status from database
     */
    @GetMapping("/status")
    public ResponseEntity<?> getSchedulerStatus() {
        try {
            Map<String, Object> status = schedulerConfigService.getSchedulerStatus();
            
            status.put("site_scheduler", getProperty("app.site_scheduler", "true"));
            status.put("health_check", schedulerConfigService.getConfigValue("app.health_check_enabled", "no"));
            
            log.info("Scheduler status requested: {}", status);
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Error getting scheduler status", e);
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Error: " + e.getMessage()));
        }
    }
    
    /**
     * Update scheduler configuration in database
     */
    @PostMapping("/config")
    public ResponseEntity<?> updateSchedulerConfig(@RequestBody Map<String, Object> config, HttpServletRequest request) {
        try {
            String updatedBy = getClientInfo(request);
            Map<String, Object> dbUpdates = new HashMap<>();
            
            // Map API keys to database keys
            if (config.containsKey("offline_check")) {
                dbUpdates.put("app.offline_check", config.get("offline_check"));
            }
            
            if (config.containsKey("online_check")) {
                dbUpdates.put("app.online_check", config.get("online_check"));
            }
            
            if (config.containsKey("site_status_corn")) {
                dbUpdates.put("app.site_status_corn", config.get("site_status_corn"));
            }
            
            if (config.containsKey("offline_check_corn")) {
                dbUpdates.put("app.offline_check_corn", config.get("offline_check_corn"));
            }
            
            if (config.containsKey("online_check_corn")) {
                dbUpdates.put("app.online_check_corn", config.get("online_check_corn"));
            }
            
            if (dbUpdates.isEmpty()) {
                return ResponseEntity.badRequest().body(new ApiResponse(false, "No valid configuration provided"));
            }
            
            Map<String, Object> result = schedulerConfigService.batchUpdateConfigs(dbUpdates, updatedBy);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", result.get("success"));
            response.put("message", result.get("message"));
            response.put("updated", result.get("updated"));
            response.put("failed", result.get("failed"));
            response.put("note", "Changes take effect immediately for all nodes");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error updating scheduler configuration", e);
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Error: " + e.getMessage()));
        }
    }
    
    /**
     * Quick toggle for main schedulers
     */
    @PostMapping("/toggle")
    public ResponseEntity<?> toggleSchedulers(@RequestBody Map<String, Boolean> toggles, HttpServletRequest request) {
        try {
            String updatedBy = getClientInfo(request);
            Map<String, Object> updated = new HashMap<>();
            
            if (toggles.containsKey("offline_check")) {
                boolean success = schedulerConfigService.updateConfig("app.offline_check", String.valueOf(toggles.get("offline_check")), updatedBy);
                if (success) updated.put("offline_check", toggles.get("offline_check"));
            }
            if (toggles.containsKey("online_check")) {
                boolean success = schedulerConfigService.updateConfig("app.online_check", String.valueOf(toggles.get("online_check")), updatedBy);
                if (success) updated.put("online_check", toggles.get("online_check"));
            }
            if (toggles.containsKey("health_check")) {
                boolean success = schedulerConfigService.updateConfig("app.health_check_enabled", String.valueOf(toggles.get("health_check")), updatedBy);
                if (success) updated.put("health_check", toggles.get("health_check"));
            }
            
            log.info("Scheduler toggles applied: {}", updated);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", !updated.isEmpty());
            response.put("message", "Schedulers toggled successfully");
            response.put("updated", updated);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error toggling schedulers", e);
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Error: " + e.getMessage()));
        }
    }
    
    /**
     * Emergency stop all schedulers
     */
    /**
     * รัน health check ทันที — เรียกจากปุ่มในหน้ารายงาน
     */
    @PostMapping("/daily-report/healthcheck/run")
    public ResponseEntity<?> runHealthCheckNow(HttpServletRequest request) {
        try {
            log.info("On-demand health check requested by: {}", getClientInfo(request));
            dailyReportScheduler.runHealthCheckNow();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Health check started — processing in background");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error triggering health check", e);
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Error: " + e.getMessage()));
        }
    }

    /**
     * เคลียร์ข้อมูลวันนี้แล้วเก็บใหม่ — เรียกจากปุ่ม "เก็บข้อมูลใหม่" หน้ารายงาน
     */
    @PostMapping("/daily-report/recollect")
    public ResponseEntity<?> recollectDailyReport(HttpServletRequest request) {
        try {
            String requestedBy = getClientInfo(request);
            log.info("Daily report recollect requested by: {}", requestedBy);
            dailyReportScheduler.recollectToday();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Recollecting daily report — data cleared and restarted");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error during daily report recollect", e);
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Error: " + e.getMessage()));
        }
    }

    /**
     * ดึงเวลาที่ตั้งไว้สำหรับ daily report
     */
    @GetMapping("/daily-report/schedule")
    public ResponseEntity<?> getDailyReportSchedule() {
        String cron = schedulerConfigService.getConfigValue("app.site_status_corn", "0 0 4 * * *");
        String[] parts = cron.trim().split("\\s+");
        Map<String, Object> response = new HashMap<>();
        response.put("cron", cron);
        try {
            response.put("hour", Integer.parseInt(parts[2]));
            response.put("minute", Integer.parseInt(parts[1]));
        } catch (Exception ignored) {}
        return ResponseEntity.ok(response);
    }

    /**
     * ตั้งเวลา daily report ใหม่จาก settings gear
     * Body: { "hour": 4, "minute": 0 }
     */
    @PostMapping("/daily-report/schedule")
    public ResponseEntity<?> setDailyReportSchedule(
            @RequestBody Map<String, Integer> body,
            HttpServletRequest request) {
        try {
            int hour = body.getOrDefault("hour", 4);
            int minute = body.getOrDefault("minute", 0);
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                return ResponseEntity.badRequest().body(new ApiResponse(false, "Invalid hour/minute"));
            }
            String newCron = String.format("0 %d %d * * *", minute, hour);
            String updatedBy = getClientInfo(request);
            schedulerConfigService.updateConfig("app.site_status_corn", newCron, updatedBy);
            siteScheduler.updateCronExpression("site_status_corn", newCron);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("cron", newCron);
            response.put("message", String.format("Daily report scheduled at %02d:%02d", hour, minute));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error setting daily report schedule", e);
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Error: " + e.getMessage()));
        }
    }

    @PostMapping("/emergency-stop")
    public ResponseEntity<?> emergencyStopAll(HttpServletRequest request) {
        try {
            String updatedBy = getClientInfo(request);
            Map<String, Object> result = schedulerConfigService.emergencyStopAll(updatedBy);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error during emergency stop", e);
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Error: " + e.getMessage()));
        }
    }
    
    /**
     * Get property value with fallback
     */
    private String getProperty(String key, String defaultValue) {
        return env.getProperty(key, defaultValue);
    }
    
    /**
     * Get client information for audit trail
     */
    private String getClientInfo(HttpServletRequest request) {
        String clientIp = request.getHeader("X-Forwarded-For");
        if (clientIp == null || clientIp.isEmpty()) {
            clientIp = request.getRemoteAddr();
        }
        return "API-" + clientIp;
    }

    
    @PostMapping("/clear-threads")
    public ResponseEntity<?> clearSchedulerThreads(HttpServletRequest request) {
        try {
            String requestedBy = getClientInfo(request);
            boolean success = clearAllSchedulerThreads();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("message", success ? "Scheduler threads cleared successfully" : "Failed to clear threads");
            response.put("requestedBy", requestedBy);
            response.put("totalThreads", Thread.activeCount());
            
            log.info("Scheduler threads clear requested by: {} | Success: {}", requestedBy, success);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error clearing scheduler threads", e);
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Error: " + e.getMessage()));
        }
    }
    
    @GetMapping("/threads/status")
    public ResponseEntity<?> getThreadStatus() {
        try {
            Runtime runtime = Runtime.getRuntime();
            Map<String, Object> status = new HashMap<>();
            status.put("totalThreads", Thread.activeCount());
            status.put("availableProcessors", runtime.availableProcessors());
            status.put("maxMemory", runtime.maxMemory());
            status.put("freeMemory", runtime.freeMemory());
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Error: " + e.getMessage()));
        }
    }
    
    @PostMapping("/interrupt/{taskType}")
    public ResponseEntity<?> interruptTasks(@PathVariable String taskType, HttpServletRequest request) {
        try {
            String requestedBy = getClientInfo(request);
            boolean success = false;
            String message;
            
            switch (taskType.toLowerCase()) {
                case "offline":
                    success = interruptTasksByPattern("offline");
                    message = "Offline check tasks interrupted";
                    break;
                case "online":
                    success = interruptTasksByPattern("online");
                    message = "Online check tasks interrupted";
                    break;
                case "ping":
                    success = interruptTasksByPattern("ping");
                    message = "Ping tasks interrupted";
                    break;
                case "health":
                    success = interruptTasksByPattern("health");
                    message = "Health check tasks interrupted";
                    break;
                case "all":
                    success = clearAllSchedulerThreads();
                    message = "All scheduler tasks interrupted";
                    break;
                default:
                    return ResponseEntity.badRequest().body(new ApiResponse(false, "Unknown task type: " + taskType));
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("message", message);
            response.put("taskType", taskType);
            response.put("requestedBy", requestedBy);
            
            log.info("Task interruption: {} | Success: {}", taskType, success);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Error: " + e.getMessage()));
        }
    }
    
    private boolean clearAllSchedulerThreads() {
        try {
            System.gc();
            ThreadGroup rootGroup = Thread.currentThread().getThreadGroup();
            while (rootGroup.getParent() != null) {
                rootGroup = rootGroup.getParent();
            }
            
            Thread[] threads = new Thread[rootGroup.activeCount()];
            int count = rootGroup.enumerate(threads, true);
            
            int interruptedCount = 0;
            for (int i = 0; i < count; i++) {
                Thread thread = threads[i];
                if (thread != null && thread.getName() != null) {
                    if (thread.getName().contains("async-") || 
                        thread.getName().contains("scheduler-") ||
                        thread.getName().contains("proveOffline") ||
                        thread.getName().contains("batch-processing")) {
                        
                        thread.interrupt();
                        interruptedCount++;
                    }
                }
            }
            
            log.info("Interrupted {} scheduler threads out of {} total", interruptedCount, count);
            return true;
        } catch (Exception e) {
            log.error("Error clearing threads", e);
            return false;
        }
    }
    
    private boolean interruptTasksByPattern(String namePattern) {
        try {
            ThreadGroup rootGroup = Thread.currentThread().getThreadGroup();
            while (rootGroup.getParent() != null) {
                rootGroup = rootGroup.getParent();
            }
            
            Thread[] threads = new Thread[rootGroup.activeCount()];
            int count = rootGroup.enumerate(threads, true);
            
            int interruptedCount = 0;
            for (int i = 0; i < count; i++) {
                Thread thread = threads[i];
                if (thread != null && thread.getName() != null) {
                    if (thread.getName().toLowerCase().contains(namePattern.toLowerCase())) {
                        thread.interrupt();
                        interruptedCount++;
                    }
                }
            }
            
            log.info("Interrupted {} threads matching pattern: {}", interruptedCount, namePattern);
            return true;
        } catch (Exception e) {
            log.error("Error interrupting tasks with pattern: {}", namePattern, e);
            return false;
        }
    }
}
