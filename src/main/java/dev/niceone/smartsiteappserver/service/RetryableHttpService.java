package dev.niceone.smartsiteappserver.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

/**
 * HTTP Service with Retry Logic and Circuit Breaker
 *
 * แก้ปัญหา:
 * - Connection Refused: 10,420 errors (20.97%)
 * - Service 10.197.73.189:3000 ล่ม: 7,472 errors
 *
 * ใช้แทน RestTemplate.exchange() ปกติ
 * ไม่ต้องใช้ @Retryable annotation (ไม่มี spring-retry dependency)
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class RetryableHttpService {

    private final SimpleCircuitBreakerService circuitBreaker;

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MS = 1000; // 1 วินาที
    private static final double BACKOFF_MULTIPLIER = 2.0;
    private static final long MAX_BACKOFF_MS = 10000; // 10 วินาที

    /**
     * เรียก HTTP request พร้อม retry และ circuit breaker
     */
    public <T> ResponseEntity<T> executeWithRetry(
            String serviceKey,
            RestTemplate restTemplate,
            String url,
            HttpMethod method,
            HttpEntity<?> requestEntity,
            Class<T> responseType) {

        // เช็ค circuit breaker ก่อน
        if (circuitBreaker.isOpen(serviceKey)) {
            log.warn("⚠️ Circuit breaker is OPEN for [{}]. Request blocked.", serviceKey);
            throw new RuntimeException("Service temporarily unavailable (Circuit Open): " + serviceKey);
        }

        Exception lastException = null;
        long backoffMs = INITIAL_BACKOFF_MS;

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                log.debug("Attempt {}/{} for {}", attempt, MAX_RETRY_ATTEMPTS, url);

                ResponseEntity<T> response = restTemplate.exchange(url, method, requestEntity, responseType);

                // สำเร็จ - record success
                circuitBreaker.recordSuccess(serviceKey);

                if (attempt > 1) {
                    log.info("✓ Request succeeded on attempt {}/{} for {}", attempt, MAX_RETRY_ATTEMPTS, serviceKey);
                }

                return response;

            } catch (Exception e) {
                lastException = e;

                // เช็คว่าเป็น error ที่ควร retry หรือไม่
                if (!isRetryableException(e)) {
                    log.error("Non-retryable error for {}: {}", serviceKey, e.getMessage());
                    circuitBreaker.recordFailure(serviceKey);
                    throw e;
                }

                // ถ้ายังไม่ถึงครั้งสุดท้าย ให้ retry
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    log.warn("Attempt {}/{} failed for {}. Retrying in {}ms. Error: {}",
                            attempt, MAX_RETRY_ATTEMPTS, serviceKey, backoffMs, e.getMessage());

                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry interrupted", ie);
                    }

                    // Exponential backoff
                    backoffMs = Math.min((long) (backoffMs * BACKOFF_MULTIPLIER), MAX_BACKOFF_MS);

                } else {
                    log.error("❌ All {} attempts failed for {}. Last error: {}",
                            MAX_RETRY_ATTEMPTS, serviceKey, e.getMessage());
                    circuitBreaker.recordFailure(serviceKey);
                }
            }
        }

        throw new RuntimeException("Failed after " + MAX_RETRY_ATTEMPTS + " attempts: " + serviceKey, lastException);
    }

    /**
     * เช็คว่า exception นี้ควร retry หรือไม่
     */
    private boolean isRetryableException(Exception e) {
        // Network errors ที่ควร retry
        if (e instanceof ConnectException ||
            e instanceof SocketTimeoutException ||
            e.getMessage() != null && (
                e.getMessage().contains("Connection refused") ||
                e.getMessage().contains("Connection reset") ||
                e.getMessage().contains("Read timed out") ||
                e.getMessage().contains("Premature close")
            )) {
            return true;
        }

        // HTTP 5xx errors ควร retry
        if (e.getMessage() != null && e.getMessage().contains("500")) {
            return true;
        }

        // Client errors (4xx) ไม่ควร retry
        return false;
    }

    /**
     * GET request with retry
     */
    public <T> ResponseEntity<T> getWithRetry(
            String serviceKey,
            RestTemplate restTemplate,
            String url,
            Class<T> responseType) {
        return executeWithRetry(serviceKey, restTemplate, url, HttpMethod.GET, null, responseType);
    }

    /**
     * POST request with retry
     */
    public <T> ResponseEntity<T> postWithRetry(
            String serviceKey,
            RestTemplate restTemplate,
            String url,
            Object requestBody,
            Class<T> responseType) {
        HttpEntity<?> entity = new HttpEntity<>(requestBody);
        return executeWithRetry(serviceKey, restTemplate, url, HttpMethod.POST, entity, responseType);
    }

    /**
     * ตัวอย่างการใช้งาน:
     *
     * @Autowired
     * private RetryableHttpService httpService;
     *
     * @Autowired
     * private RestTemplate restTemplate;
     *
     * String serviceKey = "external-api-10.197.73.189:3000";
     * String url = "http://10.197.73.189:3000/api/status";
     *
     * try {
     *     ResponseEntity<String> response = httpService.getWithRetry(
     *         serviceKey, restTemplate, url, String.class
     *     );
     *     return response.getBody();
     * } catch (Exception e) {
     *     // Fallback logic
     *     return "Service unavailable";
     * }
     */
}
