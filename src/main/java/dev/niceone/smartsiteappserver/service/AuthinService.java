package dev.niceone.smartsiteappserver.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.niceone.smartsiteappserver.dto.AuthinConfigDto;
import dev.niceone.smartsiteappserver.dto.AuthinLogDto;
import dev.niceone.smartsiteappserver.dto.AuthinLogPageDto;
import dev.niceone.smartsiteappserver.model.*;
import dev.niceone.smartsiteappserver.payload.response.PaginatedResponse;
import dev.niceone.smartsiteappserver.repository.AuthinLogsRepository;
import dev.niceone.smartsiteappserver.specification.AuthinLogsSpecifications;
import dev.niceone.smartsiteappserver.utils.MultipartInputStreamFileResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AuthinService {

    // Timeouts: without them a hung authin config service on :3001 pins request threads forever.
    private final RestTemplate restTemplate = new RestTemplateBuilder()
            .setConnectTimeout(Duration.ofSeconds(3))
            .setReadTimeout(Duration.ofSeconds(15))
            .build();
    private static final int MAX_PAGE_SIZE = 500;
    private final String authinBaseUrl = "http://127.0.0.1:3001/api/authin";
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthinLogsRepository authinLogsRepository;

    private AuthinLogModel mapToModel(AuthinLogs entity) {
        return  AuthinLogModel.builder()
                .id(String.valueOf(entity.getId()))
                .timestamp(entity.getTimestamp().atZone(ZoneId.systemDefault()))
                .site_code(entity.getSiteCode())    // 🔁 ใช้ snake_case
                .url(entity.getUrl())
                .message(entity.getMessage())
                .is_clear(entity.getIsClear())      // 🔁 ใช้ snake_case
                .build();
    }
    public ResponseEntity<?> postLog(AuthinLogDto dto) {
        return restTemplate.postForEntity(authinBaseUrl + "/logs", dto, Map.class);
    }

    public void insertLog(String siteCode, String url, String message, boolean isClear) {
        AuthinLogs log = AuthinLogs.builder()
                .timestamp(LocalDateTime.now()) // <== เทียบกับ NOW()
                .siteCode(siteCode)
                .url(url)
                .message(message)
                .isClear(isClear)
                .build();

        authinLogsRepository.save(log);
    }

    public ResponseEntity<?> getLogsOld(Map<String, String> queryParams) {
        StringBuilder url = new StringBuilder(authinBaseUrl + "/logs?");
        queryParams.forEach((k, v) -> url.append(k).append("=").append(v).append("&"));
       // return restTemplate.getForEntity(url.toString(), Map.class);

        ResponseEntity<Map> response = restTemplate.getForEntity(url.toString(), Map.class);

        try {
            // 🔄 Convert Map to PaginatedResponse<AuthinConfigModel>
            PaginatedResponse<AuthinLogModel> result = new PaginatedResponse<>();

            Map<String, Object> body = response.getBody();
            if (body != null) {
                // Map list of config items
                List<AuthinLogModel> logs = objectMapper.convertValue(
                        body.get("data"),
                        new  TypeReference<List<AuthinLogModel>>() {}
                );

                result.setData(logs);
                result.setPage((Integer) body.get("page"));
                result.setSize((Integer) body.get("size"));
                result.setTotalElements((Integer) body.get("totalElements"));
                result.setTotalPages((Integer) body.get("totalPages"));
            }
            // ตรวจสอบหรือลองแก้ไข response ตรงนี้ได้ถ้าต้องการ
            return ResponseEntity.ok(result);
        }catch (Exception ex){
            return response;
        }


    }

    public ResponseEntity<?> getLogs(Map<String, String> queryParams) {
        try {
            String siteCode = queryParams.getOrDefault("site_code", null);
            String isClearStr = queryParams.get("is_clear");
            String startStr = queryParams.get("start");
            String endStr = queryParams.get("end");

            Boolean isClear = (isClearStr != null && !isClearStr.isEmpty()) ? Boolean.parseBoolean(isClearStr) : null;

            DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
            LocalDateTime start = (startStr != null && !startStr.isEmpty())
                    ? LocalDateTime.parse(startStr, formatter)
                    : LocalDateTime.now().toLocalDate().atStartOfDay();

            LocalDateTime end = (endStr != null && !endStr.isEmpty())
                    ? LocalDateTime.parse(endStr, formatter)
                    : LocalDateTime.now().toLocalDate().atTime(23, 59, 59);

            int page = Integer.parseInt(queryParams.getOrDefault("page", "1")) - 1;
            int size = Math.min(Integer.parseInt(queryParams.getOrDefault("size", "20")), MAX_PAGE_SIZE);
            Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());

            // ใช้ Specification
            Specification<AuthinLogs> spec = AuthinLogsSpecifications.filterBy(siteCode, isClear, start, end);

            Page<AuthinLogs> pageResult = authinLogsRepository.findAll(spec, pageable);

            // แปลง Entity เป็น Model
            List<AuthinLogModel> data = pageResult.getContent().stream()
                    .map(entity -> AuthinLogModel.builder()
                            .id(entity.getId().toString())
                            .timestamp(entity.getTimestamp().atZone(ZoneId.systemDefault()))
                            .site_code(entity.getSiteCode())
                            .url(entity.getUrl())
                            .message(entity.getMessage())
                            .is_clear(entity.getIsClear())
                            .build())
                    .collect(Collectors.toList());

            PaginatedResponse<AuthinLogModel> result = new PaginatedResponse<>();
            result.setData(data);
            result.setPage(pageResult.getNumber() + 1); // เพราะ Page เริ่มที่ 0
            result.setSize(pageResult.getSize());
            result.setTotalPages(pageResult.getTotalPages());
            result.setTotalElements((int)pageResult.getTotalElements());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: " + e.getMessage());
        }
    }

    public ResponseEntity<?> getSiteConfig(String siteCode) {

        ResponseEntity<Map> response = restTemplate.getForEntity(authinBaseUrl + "/config/" + siteCode, Map.class);

        try {
            // 🔄 Convert Map to PaginatedResponse<AuthinConfigModel>
            AlarmSiteConfigModel result = new AlarmSiteConfigModel();


            Map<String, Object> body = response.getBody();
            if (body != null) {
                // Map list of config items
                result = objectMapper.convertValue(body, AlarmSiteConfigModel.class);
            }
            // ตรวจสอบหรือลองแก้ไข response ตรงนี้ได้ถ้าต้องการ
            return ResponseEntity.ok(result);
        }catch (Exception ex){
            return response;
        }
       // return restTemplate.getForEntity(authinBaseUrl + "/config/" + siteCode, Map.class);
    }

    public ResponseEntity<?> updateSiteConfig(String siteCode, Map<String, Object> body) {
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body);
        return restTemplate.exchange(authinBaseUrl + "/config/" + siteCode, HttpMethod.PUT, request, Map.class);
    }

    public ResponseEntity<?> getGlobalConfig() {

        ResponseEntity<Map> response = restTemplate.getForEntity(authinBaseUrl + "/config-global", Map.class);

        try {
            // 🔄 Convert Map to PaginatedResponse<AuthinConfigModel>
            AlarmGlobalConfigModel result = new AlarmGlobalConfigModel();


            Map<String, Object> body = response.getBody();
            if (body != null) {
                // Map list of config items
                result = objectMapper.convertValue(body, AlarmGlobalConfigModel.class);
            }
            // ตรวจสอบหรือลองแก้ไข response ตรงนี้ได้ถ้าต้องการ
            return ResponseEntity.ok(result);
        }catch (Exception ex){
            return response;
        }

       // return restTemplate.getForEntity(authinBaseUrl + "/config-global", Map.class);
    }

    public ResponseEntity<?> updateGlobalConfig(Map<String, Object> body) {
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body);
        return restTemplate.exchange(authinBaseUrl + "/config-global", HttpMethod.PUT, request, Map.class);
    }

    public ResponseEntity<?> uploadConfig(MultipartFile file, boolean isDisabled, Integer dailyLimit) throws IOException {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new MultipartInputStreamFileResource(file.getInputStream(), file.getOriginalFilename()));
        body.add("is_disabled", String.valueOf(isDisabled));
        body.add("daily_limit", dailyLimit != null ? String.valueOf(dailyLimit) : "");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
        return restTemplate.postForEntity(authinBaseUrl + "/config/upload", request, Map.class);
    }

    public ResponseEntity<?> getConfigList(Map<String, String> queryParams) {
        StringBuilder url = new StringBuilder(authinBaseUrl + "/config?");
        queryParams.forEach((k, v) -> url.append(k).append("=").append(v).append("&"));

        ResponseEntity<Map> response = restTemplate.getForEntity(url.toString(), Map.class);

        try {
            // 🔄 Convert Map to PaginatedResponse<AuthinConfigModel>
            PaginatedResponse<AuthinConfigModel> result = new PaginatedResponse<>();

            Map<String, Object> body = response.getBody();
            if (body != null) {
                // Map list of config items
                List<AuthinConfigModel> configs = objectMapper.convertValue(
                        body.get("data"),
                        new  TypeReference<List<AuthinConfigModel>>() {}
                );

                result.setData(configs);
                result.setPage((Integer) body.get("page"));
                result.setSize((Integer) body.get("size"));
                result.setTotalElements((Integer) body.get("totalElements"));
                result.setTotalPages((Integer) body.get("totalPages"));
            }
            // ตรวจสอบหรือลองแก้ไข response ตรงนี้ได้ถ้าต้องการ
            return ResponseEntity.ok(result);
        }catch (Exception ex){
            return response;
        }

    }
}
