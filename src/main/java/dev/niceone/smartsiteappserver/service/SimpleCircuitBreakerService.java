package dev.niceone.smartsiteappserver.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple Circuit Breaker Implementation
 *
 * แก้ปัญหา: 10.197.73.189:3000 ล่ม ทำให้เกิด 7,472 errors (15% ของปัญหาทั้งหมด)
 * - ไม่ต้องใช้ Resilience4j (เพราะ offline server)
 * - ใช้ in-memory state management
 * - Auto recovery หลังจากพัก
 */
@Service
@Log4j2
public class SimpleCircuitBreakerService {

    private static final int FAILURE_THRESHOLD = 5; // เปิด circuit หลัง fail 5 ครั้ง
    private static final long OPEN_DURATION_MS = 60000; // พัก 60 วินาทีก่อนลองใหม่
    private static final long HALF_OPEN_SUCCESS_COUNT = 2; // ต้อง succeed 2 ครั้งก่อนปิด circuit

    private final Map<String, CircuitState> circuits = new ConcurrentHashMap<>();

    public enum State {
        CLOSED,      // ปกติ ให้ request ผ่าน
        OPEN,        // หยุดส่ง request ชั่วคราว
        HALF_OPEN    // ทดลองส่ง request ดู
    }

    private static class CircuitState {
        State state = State.CLOSED;
        int failureCount = 0;
        int successCount = 0;
        LocalDateTime lastFailureTime;
        LocalDateTime openedAt;
    }

    /**
     * เช็คว่า circuit เปิดหรือไม่ ถ้าเปิดจะไม่ให้เรียก service
     */
    public boolean isOpen(String serviceKey) {
        CircuitState circuit = circuits.computeIfAbsent(serviceKey, k -> new CircuitState());

        synchronized (circuit) {
            if (circuit.state == State.OPEN) {
                // เช็คว่าถึงเวลา retry หรือยัง
                if (circuit.openedAt != null &&
                    LocalDateTime.now().isAfter(circuit.openedAt.plusNanos(OPEN_DURATION_MS * 1_000_000))) {
                    // เปลี่ยนเป็น HALF_OPEN เพื่อทดลอง
                    circuit.state = State.HALF_OPEN;
                    circuit.successCount = 0;
                    log.info("Circuit [{}] changed to HALF_OPEN, will try limited requests", serviceKey);
                    return false;
                } else {
                    return true; // ยังไม่ถึงเวลา ให้รอต่อ
                }
            }

            if (circuit.state == State.HALF_OPEN) {
                // อนุญาตให้ส่ง request เพื่อทดสอบ
                return false;
            }

            return false; // CLOSED state
        }
    }

    /**
     * บันทึกว่า request สำเร็จ
     */
    public void recordSuccess(String serviceKey) {
        CircuitState circuit = circuits.get(serviceKey);
        if (circuit == null) return;

        synchronized (circuit) {
            if (circuit.state == State.HALF_OPEN) {
                circuit.successCount++;
                if (circuit.successCount >= HALF_OPEN_SUCCESS_COUNT) {
                    circuit.state = State.CLOSED;
                    circuit.failureCount = 0;
                    circuit.successCount = 0;
                    log.info("✓ Circuit [{}] CLOSED - Service recovered", serviceKey);
                }
            } else if (circuit.state == State.CLOSED) {
                // Reset failure count on success
                circuit.failureCount = 0;
            }
        }
    }

    /**
     * บันทึกว่า request ล้มเหลว
     */
    public void recordFailure(String serviceKey) {
        CircuitState circuit = circuits.computeIfAbsent(serviceKey, k -> new CircuitState());

        synchronized (circuit) {
            circuit.failureCount++;
            circuit.lastFailureTime = LocalDateTime.now();

            if (circuit.state == State.HALF_OPEN) {
                // ถ้า HALF_OPEN แล้ว fail อีก ให้เปิด circuit ทันที
                circuit.state = State.OPEN;
                circuit.openedAt = LocalDateTime.now();
                circuit.failureCount = 0;
                log.error("⚠️ Circuit [{}] OPENED again - Service still failing", serviceKey);
            } else if (circuit.state == State.CLOSED && circuit.failureCount >= FAILURE_THRESHOLD) {
                // ถ้า fail เกิน threshold ให้เปิด circuit
                circuit.state = State.OPEN;
                circuit.openedAt = LocalDateTime.now();
                log.error("⚠️ Circuit [{}] OPENED - {} consecutive failures detected. " +
                        "Blocking requests for {} seconds",
                        serviceKey, circuit.failureCount, OPEN_DURATION_MS / 1000);
            } else {
                log.warn("Circuit [{}] failure count: {}/{}",
                    serviceKey, circuit.failureCount, FAILURE_THRESHOLD);
            }
        }
    }

    /**
     * Get current state for monitoring
     */
    public State getState(String serviceKey) {
        CircuitState circuit = circuits.get(serviceKey);
        return circuit != null ? circuit.state : State.CLOSED;
    }

    /**
     * Force reset circuit (สำหรับ manual intervention)
     */
    public void reset(String serviceKey) {
        CircuitState circuit = circuits.get(serviceKey);
        if (circuit != null) {
            synchronized (circuit) {
                circuit.state = State.CLOSED;
                circuit.failureCount = 0;
                circuit.successCount = 0;
                log.info("Circuit [{}] manually RESET", serviceKey);
            }
        }
    }

    /**
     * ตัวอย่างการใช้งาน:
     *
     * String serviceKey = "external-service-10.197.73.189:3000";
     *
     * if (circuitBreaker.isOpen(serviceKey)) {
     *     // ไม่เรียก service เพราะ circuit เปิดอยู่
     *     return fallbackResponse();
     * }
     *
     * try {
     *     ResponseEntity<?> response = restTemplate.getForEntity(url, String.class);
     *     circuitBreaker.recordSuccess(serviceKey);
     *     return response;
     * } catch (Exception e) {
     *     circuitBreaker.recordFailure(serviceKey);
     *     throw e;
     * }
     */
}
