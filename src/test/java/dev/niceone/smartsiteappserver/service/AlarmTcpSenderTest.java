package dev.niceone.smartsiteappserver.service;

import dev.niceone.smartsiteappserver.dto.AuthinLogDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class AlarmTcpSenderTest {

    private AuthinService authin;
    private MockEnvironment env;
    private AlarmTcpSender sender;

    @BeforeEach
    void setUp() {
        authin = mock(AuthinService.class);
        env = new MockEnvironment().withProperty("app.alarm_ip", "127.0.0.1");
        sender = new AlarmTcpSender();
        ReflectionTestUtils.setField(sender, "env", env);
        ReflectionTestUtils.setField(sender, "authinService", authin);
        sender.retryDelayMs = 10;
        sender.start();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        sender.stop();
    }

    private static AuthinLogDto dto(boolean clear) {
        AuthinLogDto d = new AuthinLogDto();
        d.setSiteCode("NSRKK");
        d.setIsClear(clear);
        return d;
    }

    @Test
    void sendsEachMessageOnItsOwnConnectionInOrder() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            env.setProperty("app.alarm_port", String.valueOf(server.getLocalPort()));
            List<String> received = Collections.synchronizedList(new ArrayList<>());
            Thread reader = new Thread(() -> {
                for (int i = 0; i < 3; i++) {
                    try (Socket s = server.accept(); InputStream in = s.getInputStream()) {
                        ByteArrayOutputStream buf = new ByteArrayOutputStream();
                        byte[] b = new byte[256];
                        for (int n; (n = in.read(b)) > 0; ) buf.write(b, 0, n);
                        received.add(new String(buf.toByteArray(), StandardCharsets.UTF_8));
                    } catch (Exception e) {
                        return;
                    }
                }
            });
            reader.start();

            sender.send("raise-1", dto(false));
            sender.send("clear-1", dto(true));
            sender.send("raise-2", dto(false));
            reader.join(5000);

            assertEquals(Arrays.asList("raise-1", "clear-1", "raise-2"), received);
            verify(authin, timeout(2000).times(3)).insertLog(eq("NSRKK"), anyString(), startsWith(""), anyBoolean());
            verify(authin).insertLog(eq("NSRKK"), anyString(), eq("clear-1,[SUCCESS]"), eq(true));
        }
    }

    @Test
    void failedSendIsRetriedThenLoggedAsFail() throws Exception {
        int closedPort;
        try (ServerSocket s = new ServerSocket(0)) {
            closedPort = s.getLocalPort();
        }
        env.setProperty("app.alarm_port", String.valueOf(closedPort));

        sender.send("raise-1", dto(false));

        // A refused connect takes about 2 s on Windows, so allow for 3 attempts
        verify(authin, timeout(20000).times(1))
                .insertLog(eq("NSRKK"), eq("127.0.0.1:" + closedPort), startsWith("raise-1,[FAIL:sendTcpAlarm I/O error"), eq(false));
        assertEquals(0, sender.queuedCount());
    }
}
