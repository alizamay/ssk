package dev.niceone.smartsiteappserver.service;

import dev.niceone.smartsiteappserver.model.Site;
import dev.niceone.smartsiteappserver.repository.SiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeviceRebootServiceTest {

    private static final String IP = "10.247.187.146";

    /** Replaces the real ssh/adb process with queued results and records the commands. */
    static class FakeProcessService extends DeviceRebootService {
        final Deque<ProcessResult> results = new ArrayDeque<>();
        final List<List<String>> commands = new ArrayList<>();

        @Override
        ProcessResult runProcess(List<String> command) {
            commands.add(command);
            return results.isEmpty() ? new ProcessResult(0, "") : results.poll();
        }
    }

    private FakeProcessService service;

    @BeforeEach
    void setUp() {
        SiteRepository repository = mock(SiteRepository.class);
        Site site = new Site();
        site.setCode("BHSKW");
        site.setIp_address(IP);
        when(repository.findByCode("BHSKW")).thenReturn(Optional.of(site));

        service = new FakeProcessService();
        ReflectionTestUtils.setField(service, "siteRepository", repository);
        ReflectionTestUtils.setField(service, "sshCommand", "ssh");
        ReflectionTestUtils.setField(service, "sshUser", "pi");
        ReflectionTestUtils.setField(service, "sshKey", "");
        ReflectionTestUtils.setField(service, "sshOptions", "-o BatchMode=yes");
        ReflectionTestUtils.setField(service, "sshRebootCommand", "sudo reboot");
        ReflectionTestUtils.setField(service, "adbPath", "adb");
        ReflectionTestUtils.setField(service, "adbPort", "5555");
        ReflectionTestUtils.setField(service, "timeoutSec", 15L);
    }

    private Map<String, Object> rebootPi() {
        return service.rebootByCode("BHSKW", "RaspberryPi");
    }

    private Map<String, Object> rebootAndroid() {
        return service.rebootByCode("BHSKW", "Android");
    }

    private static void assertFailed(Map<String, Object> resp) {
        assertEquals(false, resp.get("isSuccess"));
        assertEquals(DeviceRebootService.MSG_FAILED, resp.get("message"));
    }

    @Test
    void piConnectTimeoutIsFailureWithOutput() {
        String output = "Warning: Identity file /root/.ssh/id_rsa not accessible: No such file or directory.\n"
                + "ssh: connect to host " + IP + " port 22: Connection timed out\n";
        service.results.add(new DeviceRebootService.ProcessResult(255, output));

        Map<String, Object> resp = rebootPi();

        assertFailed(resp);
        assertEquals(255, resp.get("exitCode"));
        assertTrue(((String) resp.get("output")).contains("Connection timed out"));
        assertTrue(((String) resp.get("errorDetail")).contains("could not reach"));
    }

    @Test
    void piMissingKeyFailsBeforeRunningSsh() {
        String missing = new File("does-not-exist", "id_rsa").getPath();
        ReflectionTestUtils.setField(service, "sshKey", missing);

        Map<String, Object> resp = rebootPi();

        assertFailed(resp);
        assertEquals("SSH key not found: " + missing, resp.get("errorDetail"));
        assertTrue(service.commands.isEmpty(), "ssh must not run without its key");
    }

    @Test
    void piExistingKeyIsPassedToSsh(@TempDir Path dir) throws Exception {
        Path key = Files.createFile(dir.resolve("id_ed25519"));
        ReflectionTestUtils.setField(service, "sshKey", key.toString());
        service.results.add(new DeviceRebootService.ProcessResult(0, ""));

        Map<String, Object> resp = rebootPi();

        assertEquals(true, resp.get("isSuccess"));
        assertTrue(service.commands.get(0).contains(key.toString()));
    }

    @Test
    void piSessionClosedByRebootIsSuccess() {
        service.results.add(new DeviceRebootService.ProcessResult(255,
                "Connection to " + IP + " closed by remote host.\n"));

        Map<String, Object> resp = rebootPi();

        assertEquals(true, resp.get("isSuccess"));
        assertEquals(DeviceRebootService.MSG_SENT, resp.get("message"));
    }

    @Test
    void piExitZeroIsSuccess() {
        service.results.add(new DeviceRebootService.ProcessResult(0, ""));

        assertEquals(true, rebootPi().get("isSuccess"));
    }

    @Test
    void piAuthFailureIsFailure() {
        service.results.add(new DeviceRebootService.ProcessResult(255,
                "pi@" + IP + ": Permission denied (publickey,password).\n"));

        assertFailed(rebootPi());
    }

    @Test
    void piSudoErrorIsFailure() {
        service.results.add(new DeviceRebootService.ProcessResult(1,
                "sudo: a password is required\n"));

        assertFailed(rebootPi());
    }

    @Test
    void piProcessTimeoutIsFailure() {
        DeviceRebootService.ProcessResult hung = new DeviceRebootService.ProcessResult(-1, "");
        hung.timedOut = true;
        service.results.add(hung);

        assertFailed(rebootPi());
    }

    @Test
    void androidConnectFailureIsFailureAndSkipsReboot() {
        // adb connect exits 0 even when it cannot connect
        service.results.add(new DeviceRebootService.ProcessResult(0,
                "failed to connect to '" + IP + ":5555': Connection refused\n"));

        Map<String, Object> resp = rebootAndroid();

        assertFailed(resp);
        assertTrue(((String) resp.get("output")).contains("Connection refused"));
        assertEquals(1, service.commands.size(), "reboot must not run after a failed connect");
    }

    @Test
    void androidConnectedAndRebootedIsSuccess() {
        service.results.add(new DeviceRebootService.ProcessResult(0, "connected to " + IP + ":5555\n"));
        service.results.add(new DeviceRebootService.ProcessResult(0, ""));
        service.results.add(new DeviceRebootService.ProcessResult(0, "disconnected " + IP + ":5555\n"));

        Map<String, Object> resp = rebootAndroid();

        assertEquals(true, resp.get("isSuccess"));
        assertTrue(((String) resp.get("output")).contains("connect: connected to"));
        assertEquals(3, service.commands.size());
    }

    @Test
    void androidRebootOnOfflineDeviceIsFailure() {
        service.results.add(new DeviceRebootService.ProcessResult(0, "already connected to " + IP + ":5555\n"));
        service.results.add(new DeviceRebootService.ProcessResult(1, "error: device offline\n"));
        service.results.add(new DeviceRebootService.ProcessResult(0, ""));

        Map<String, Object> resp = rebootAndroid();

        assertFailed(resp);
        assertTrue(((String) resp.get("output")).contains("device offline"));
    }

    @Test
    void unknownDeviceTypeIsFailure() {
        Map<String, Object> resp = service.rebootByCode("BHSKW", "Toaster");

        assertFailed(resp);
        assertFalse(service.commands.iterator().hasNext());
    }
}
