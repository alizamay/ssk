package dev.niceone.smartsiteappserver.service;

import com.google.common.util.concurrent.RateLimiter;
import dev.niceone.smartsiteappserver.dto.AuthinLogDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sends alarm messages to Centralized Fault, one TCP connection per message, from a single
 * thread at a capped rate.
 *
 * Callers used to open the socket themselves. When thousands of sites recovered at once
 * (e.g. after a network outage) every comm-lost clear opened a connection within seconds,
 * the node ran out of local ports ("Address already in use: connect") and those clears were
 * lost. Now messages queue here, leave in order at app.alarm.send_per_sec, and a failed send
 * is retried before it is logged as FAIL.
 */
@Component
@Slf4j
public class AlarmTcpSender {

    @Value("${app.alarm.send_per_sec:100}")
    private double sendPerSec = 100;

    @Value("${app.alarm.send_retries:3}")
    private int retries = 3;

    long retryDelayMs = 1000;

    @Autowired
    private Environment env;

    @Autowired
    private AuthinService authinService;

    private ExecutorService sender;
    private RateLimiter limiter;
    private final AtomicInteger queued = new AtomicInteger();

    @PostConstruct
    void start() {
        limiter = RateLimiter.create(sendPerSec > 0 ? sendPerSec : 100);
        sender = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "alarm-sender");
            t.setDaemon(true);
            return t;
        });
    }

    /** Lets queued alarms go out on undeploy instead of dropping them. */
    @PreDestroy
    void stop() throws InterruptedException {
        sender.shutdown();
        if (!sender.awaitTermination(10, TimeUnit.SECONDS)) {
            log.warn("alarm sender stopped with {} alarms not sent", queued.get());
            sender.shutdownNow();
        }
    }

    public void send(String alarm, AuthinLogDto dto) {
        int q = queued.incrementAndGet();
        if (q % 1000 == 0) {
            log.warn("alarm send queue at {} messages", q);
        }
        sender.execute(() -> {
            try {
                deliver(alarm, dto);
            } catch (RuntimeException e) {
                log.warn("alarm send failed: {}", e.toString());
            } finally {
                queued.decrementAndGet();
            }
        });
    }

    public int queuedCount() {
        return queued.get();
    }

    private void deliver(String alarm, AuthinLogDto dto) {
        String ip = env.getProperty("app.alarm_ip");
        String port = env.getProperty("app.alarm_port");
        dto.setUrl(ip + ":" + port);
        log.info(alarm);
        limiter.acquire();

        String failure = null;
        for (int attempt = 1; attempt <= Math.max(1, retries); attempt++) {
            try {
                write(ip, Integer.parseInt(port), alarm);
                failure = null;
                break;
            } catch (UnknownHostException e) {
                failure = "Server not found " + e.getMessage();
                break;
            } catch (IOException e) {
                failure = "I/O error " + e.getMessage();
                if (attempt < retries) {
                    try {
                        Thread.sleep(retryDelayMs * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        if (failure == null) {
            dto.setMessage(alarm + ",[SUCCESS]");
        } else {
            log.error("sendTcpAlarm {} after {} attempt(s): {}", failure, retries, alarm);
            dto.setMessage(alarm + ",[FAIL:sendTcpAlarm " + failure + "]");
        }
        authinService.insertLog(dto.getSiteCode(), dto.getUrl(), dto.getMessage(), Boolean.TRUE.equals(dto.getIsClear()));
    }

    private static void write(String ip, int port, String alarm) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), 5000);
            socket.setSoTimeout(5000);
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
            writer.print(alarm);
            writer.flush();
            if (writer.checkError()) {
                throw new IOException("write failed");
            }
        }
    }
}
