package dev.niceone.smartsiteappserver.service;

import dev.niceone.smartsiteappserver.exception.ResourceNotFoundException;
import dev.niceone.smartsiteappserver.model.Site;
import dev.niceone.smartsiteappserver.repository.SiteRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class DeviceRebootService {

    static final String MSG_SENT = "Reboot command sent";
    static final String MSG_FAILED = "Reboot FAILED";

    // ssh output that means the command never reached the device (connect or auth failed)
    private static final List<String> SSH_CONNECT_ERRORS = Arrays.asList(
            "connection timed out", "connection refused", "no route to host", "network is unreachable",
            "not accessible", "permission denied", "could not resolve", "host key verification failed",
            "name or service not known");

    // adb output that means connect or reboot did not reach the device
    private static final List<String> ADB_ERRORS = Arrays.asList(
            "unable to connect", "failed to connect", "cannot connect", "device offline",
            "connection refused", "no route to host", "timed out", "not found", "error:");

    @Autowired
    private SiteRepository siteRepository;

    @Value("${device.ssh.command:ssh}")
    private String sshCommand;

    @Value("${device.ssh.user:pi}")
    private String sshUser;

    // Empty = ssh uses the default keys of the user running the app server (no root needed)
    @Value("${device.ssh.key:}")
    private String sshKey;

    // ServerAlive ends the session a few seconds after the device goes down for reboot,
    // instead of hanging until the process timeout
    @Value("${device.ssh.options:-o StrictHostKeyChecking=no -o ConnectTimeout=10 -o BatchMode=yes -o ServerAliveInterval=2 -o ServerAliveCountMax=2}")
    private String sshOptions;

    @Value("${device.ssh.reboot-command:sudo reboot}")
    private String sshRebootCommand;

    @Value("${device.adb.path:adb}")
    private String adbPath;

    @Value("${device.adb.port:5555}")
    private String adbPort;

    @Value("${device.reboot.timeout-sec:15}")
    private long timeoutSec;

    public Map<String, Object> reboot(Long siteId, String deviceType) {
        Site site = siteRepository.findById(siteId).orElseThrow(
                () -> new ResourceNotFoundException("Site", "id", siteId));
        return rebootSite(site, deviceType);
    }

    public Map<String, Object> rebootByCode(String code, String deviceType) {
        Site site = siteRepository.findByCode(code).orElseThrow(
                () -> new ResourceNotFoundException("Site", "code", code));
        return rebootSite(site, deviceType);
    }

    private Map<String, Object> rebootSite(Site site, String deviceType) {
        String ip = site.getIp_address();
        if (ip == null || ip.isEmpty()) {
            return error(site.getCode(), "Site has no IP address");
        }

        String type = normalizeDeviceType(deviceType);
        log.info("reboot request site={} ip={} deviceType={}", site.getCode(), ip, type);

        if ("ANDROID".equals(type)) {
            return rebootAndroid(site.getCode(), ip);
        } else if ("PI".equals(type) || "RASPBERRYPI".equals(type)) {
            return rebootPi(site.getCode(), ip);
        } else {
            return error(site.getCode(), "Unknown device_type: " + deviceType + " (expected 'Android' or 'RaspberryPi')");
        }
    }

    private String normalizeDeviceType(String deviceType) {
        if (deviceType == null) return "";
        return deviceType.trim().toUpperCase().replace(" ", "");
    }

    private Map<String, Object> rebootPi(String siteCode, String ip) {
        List<String> cmd = new ArrayList<>();
        cmd.add(sshCommand);
        if (sshKey != null && !sshKey.trim().isEmpty()) {
            File key = new File(sshKey.trim());
            if (!key.isFile()) {
                return piResp(false, siteCode, ip, -1, "", "SSH key not found: " + key.getPath());
            }
            if (!key.canRead()) {
                return piResp(false, siteCode, ip, -1, "", "SSH key not readable by the app server user: " + key.getPath());
            }
            cmd.add("-i");
            cmd.add(key.getPath());
        }
        if (sshOptions != null && !sshOptions.isEmpty()) {
            for (String opt : sshOptions.trim().split("\\s+")) {
                if (!opt.isEmpty()) cmd.add(opt);
            }
        }
        cmd.add(sshUser + "@" + ip);
        cmd.add(sshRebootCommand);

        log.info("rebootPi site={} cmd={}", siteCode, cmd);
        ProcessResult r = runProcess(cmd);

        String detail = null;
        if (r.timedOut) {
            detail = "ssh did not finish within " + timeoutSec + "s";
        } else if (r.exitCode == 0) {
            detail = null;
        } else if (r.exitCode == 255) {
            // A successful "sudo reboot" drops the session, so ssh exits 255 then too.
            // It is a failure only when the output shows ssh never connected or authenticated.
            String connectError = firstMatch(r.output, SSH_CONNECT_ERRORS);
            if (connectError != null) {
                detail = "ssh could not reach the device (" + connectError + ")";
            }
        } else {
            detail = "ssh exited with code " + r.exitCode;
        }
        return piResp(detail == null, siteCode, ip, r.exitCode, r.output, detail);
    }

    private Map<String, Object> rebootAndroid(String siteCode, String ip) {
        String target = ip + ":" + adbPort;
        List<String> connectCmd = Arrays.asList(adbPath, "connect", target);
        List<String> rebootCmd = Arrays.asList(adbPath, "-s", target, "reboot");
        List<String> disconnectCmd = Arrays.asList(adbPath, "disconnect", target);

        log.info("rebootAndroid site={} cmd={}", siteCode, connectCmd);

        // adb connect exits 0 even when it fails, so the output decides
        ProcessResult conn = runProcess(connectCmd);
        String connError = adbError(conn);
        if (connError == null && !safe(conn.output).toLowerCase(Locale.ROOT).contains("connected to")) {
            connError = "unexpected output";
        }
        if (connError != null) {
            return androidResp(false, siteCode, ip, conn.exitCode,
                    "connect: " + safe(conn.output), "ADB connect failed (" + connError + ")");
        }

        ProcessResult reboot = runProcess(rebootCmd);
        ProcessResult disc = runProcess(disconnectCmd);
        String rebootError = adbError(reboot);

        String combined = "connect: " + safe(conn.output)
                + "\nreboot: " + safe(reboot.output)
                + "\ndisconnect: " + safe(disc.output);
        return androidResp(rebootError == null, siteCode, ip, reboot.exitCode, combined,
                rebootError == null ? null : "ADB reboot failed (" + rebootError + ")");
    }

    private String adbError(ProcessResult r) {
        if (r.timedOut) return "no response within " + timeoutSec + "s";
        String match = firstMatch(r.output, ADB_ERRORS);
        if (match != null) return match;
        if (r.exitCode != 0) return "exit code " + r.exitCode;
        return null;
    }

    private Map<String, Object> piResp(boolean ok, String siteCode, String ip, int exitCode, String output, String errorDetail) {
        return result(ok, "RaspberryPi", siteCode, ip, exitCode, output, errorDetail);
    }

    private Map<String, Object> androidResp(boolean ok, String siteCode, String ip, int exitCode, String output, String errorDetail) {
        return result(ok, "Android", siteCode, ip, exitCode, output, errorDetail);
    }

    private Map<String, Object> result(boolean ok, String deviceType, String siteCode, String ip,
                                       int exitCode, String output, String errorDetail) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("isSuccess", ok);
        resp.put("deviceType", deviceType);
        resp.put("siteCode", siteCode);
        resp.put("ip", ip);
        resp.put("exitCode", exitCode);
        resp.put("output", safe(output));
        resp.put("message", ok ? MSG_SENT : MSG_FAILED);
        if (ok) {
            log.info("reboot sent site={} ip={} deviceType={} exitCode={}", siteCode, ip, deviceType, exitCode);
        } else {
            resp.put("errorDetail", errorDetail);
            log.warn("reboot FAILED site={} ip={} deviceType={} exitCode={} detail={} output={}",
                    siteCode, ip, deviceType, exitCode, errorDetail, oneLine(output));
        }
        return resp;
    }

    private Map<String, Object> error(String siteCode, String detail) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("isSuccess", false);
        resp.put("siteCode", siteCode);
        resp.put("message", MSG_FAILED);
        resp.put("errorDetail", detail);
        resp.put("output", "");
        log.warn("reboot FAILED site={} detail={}", siteCode, detail);
        return resp;
    }

    private static String firstMatch(String output, List<String> patterns) {
        String lower = safe(output).toLowerCase(Locale.ROOT);
        for (String p : patterns) {
            if (lower.contains(p)) return p;
        }
        return null;
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static String oneLine(String s) {
        String flat = safe(s).replaceAll("\\s*\\n\\s*", " | ");
        return flat.length() > 500 ? flat.substring(0, 500) + "..." : flat;
    }

    // Package-private so tests can replace the real process with a canned result
    ProcessResult runProcess(List<String> command) {
        ProcessResult result = new ProcessResult();
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            process = pb.start();

            StringBuilder out = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    out.append(line).append('\n');
                    if (out.length() > 8192) break;
                }
            }

            boolean finished = process.waitFor(timeoutSec, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                result.timedOut = true;
                result.exitCode = -1;
            } else {
                result.exitCode = process.exitValue();
            }
            result.output = out.toString();
        } catch (Exception e) {
            log.warn("runProcess failed: {} | {}", command, e.getMessage());
            result.exitCode = -1;
            result.output = (result.output == null ? "" : result.output) + "ERROR: " + e.getMessage();
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
        return result;
    }

    static class ProcessResult {
        int exitCode = -1;
        String output = "";
        boolean timedOut = false;

        ProcessResult() {
        }

        ProcessResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }
}
