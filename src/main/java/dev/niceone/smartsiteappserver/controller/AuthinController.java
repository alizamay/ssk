package dev.niceone.smartsiteappserver.controller;

import com.opencsv.CSVWriter;
import dev.niceone.smartsiteappserver.dto.AuthinConfigDto;
import dev.niceone.smartsiteappserver.dto.AuthinLogDto;
import dev.niceone.smartsiteappserver.dto.AuthinLogPageDto;
import dev.niceone.smartsiteappserver.model.AuthinLogs;
import dev.niceone.smartsiteappserver.repository.AuthinLogsRepository;
import dev.niceone.smartsiteappserver.service.AuthinService;
import dev.niceone.smartsiteappserver.specification.AuthinLogsSpecifications;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/internal/authin")
public class AuthinController {

    @Autowired
    private AuthinService authinService;

    @Autowired
    private AuthinLogsRepository authinLogsRepository;

    @PostMapping("/logs")
    public ResponseEntity<?> postLog(@RequestBody AuthinLogDto dto) {
        //return authinService.postLog(dto);
        authinService.insertLog(dto.getSiteCode(), dto.getUrl(), dto.getMessage(), dto.getIsClear());
        return ResponseEntity.ok("Log saved");
    }

    @GetMapping("/logs")
    public ResponseEntity<?> getLogs(@RequestParam Map<String, String> params) {
        return authinService.getLogs(params);
    }

    @GetMapping("/config/{siteCode}")
    public ResponseEntity<?> getSiteConfig(@PathVariable String siteCode) {
        return authinService.getSiteConfig(siteCode);
    }

    @PutMapping("/config/{siteCode}")
    public ResponseEntity<?> updateSiteConfig(@PathVariable String siteCode, @RequestBody Map<String, Object> body) {
        return authinService.updateSiteConfig(siteCode, body);
    }

    @GetMapping("/config-global")
    public ResponseEntity<?> getGlobalConfig() {
        return authinService.getGlobalConfig();
    }

    @PutMapping("/config-global")
    public ResponseEntity<?> updateGlobalConfig(@RequestBody Map<String, Object> body) {
        return authinService.updateGlobalConfig(body);
    }

    @PostMapping("/config/upload")
    public ResponseEntity<?> uploadConfig(
            @RequestPart("file") MultipartFile file,
            @RequestParam("is_disabled") boolean isDisabled,
            @RequestParam(value = "daily_limit", required = false) Integer dailyLimit
    ) throws IOException {
        return authinService.uploadConfig(file, isDisabled, dailyLimit);
    }

    @GetMapping("/config")
    public ResponseEntity<?> getConfigList(@RequestParam Map<String, String> params) {
        return authinService.getConfigList(params);
    }

    @GetMapping("/export")
    public void exportCsv(
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) Boolean isClear,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            HttpServletResponse response
    ) throws IOException {
        // Without a range this loaded the whole authin_logs table (~100k rows/day) into memory.
        // Default to today, cap the range, and stream page by page.
        LocalDateTime end = to != null ? to : LocalDateTime.now();
        LocalDateTime start = from != null ? from : end.toLocalDate().atStartOfDay();
        if (start.isAfter(end) || Duration.between(start, end).toDays() > MAX_EXPORT_DAYS) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "from/to must be a range of at most " + MAX_EXPORT_DAYS + " days");
            return;
        }

        Specification<AuthinLogs> spec = Specification
                .where(AuthinLogsSpecifications.siteCodeLike(siteCode))
                .and(AuthinLogsSpecifications.isClear(isClear))
                .and(AuthinLogsSpecifications.timestampBetween(start, end));

        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"authin_logs.csv\"");

        try (CSVWriter writer = new CSVWriter(response.getWriter())) {
            writer.writeNext(new String[]{"ID", "site_code", "is_clear", "timestamp", "message"});

            Pageable pageable = PageRequest.of(0, EXPORT_PAGE_SIZE, Sort.by("timestamp", "id"));
            Slice<AuthinLogs> slice;
            do {
                slice = authinLogsRepository.findAll(spec, pageable);
                for (AuthinLogs log : slice.getContent()) {

                    String statusName = "Create";

                    if(log.getIsClear()){
                        statusName = "Clear";
                    }

                    writer.writeNext(new String[]{
                            String.valueOf(log.getId()),
                            log.getSiteCode(),
                            statusName,
                            log.getTimestamp().toString(),
                            log.getMessage()
                    });
                }
                writer.flush();
                pageable = slice.nextPageable();
            } while (slice.hasNext());
        }
    }

    private static final int MAX_EXPORT_DAYS = 31;
    private static final int EXPORT_PAGE_SIZE = 5000;
}
