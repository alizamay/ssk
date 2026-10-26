package dev.niceone.smartsiteappserver.service;

import dev.niceone.smartsiteappserver.exception.ResourceNotFoundException;
import dev.niceone.smartsiteappserver.model.Site;
import dev.niceone.smartsiteappserver.repository.SiteRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class DeviceRebootService {

    @Autowired
    private SiteRepository siteRepository;

    @Value("${device.ssh.command:ssh}")
    private String sshCommand;

    @Value("${device.ssh.user:pi}")
    private String sshUser;

    @Value("${device.ssh.key:}")
    private String sshKey;

    @Value("${device.ssh.options:-o StrictHostKeyChecking=no -o ConnectTimeout=10 -o BatchMode=yes}")
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
            return error("Site has no IP address");
        }

        String type = normalizeDeviceType(deviceType);
        log.info("reboot request site={} ip={} deviceType={}", site.getCode(), ip, type);

        if ("ANDROID".equals(type)) {
            return rebootAndroid(site.getCode(), ip);
        } else if ("PI".equals(type) || "RASPBERRYPI".equals(type)) {
            return rebootPi(site.getCode(), ip);
        } else {
            return error("Unknown device_type: " + deviceType + " (expected 'Android' or 'RaspberryPi')");
        }
    }

    private String normalizeDeviceType(String deviceType) {
        if (deviceType == null) return "";
        return deviceType.trim().toUpperCase().replace("RASPBERRY", "RASPBERRY").replace(" ", "");
    }

    private Map<String, Object> rebootPi(String siteCode, String ip) {
        List<String> cmd = new ArrayList<>();
        cmd.add(sshCommand);
        if (sshKey != null && !sshKey.isEmpty()) {
            cmd.add("-i");
            cmd.add(sshKey);
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

        // sudo reboot kills the ssh session — exit code 255 / -1 is common and acceptable
        boolean accepted = r.exitCode == 0 || r.exitCode == 255 || r.timedOut;
        Map<String, Object> resp = new HashMap<>();
        resp.put("isSuccess", accepted);
        resp.put("deviceType", "RaspberryPi");
        resp.put("siteCode", siteCode);
        resp.put("ip", ip);
        resp.put("exitCode", r.exitCode);
        resp.put("output", r.output);
        resp.put("message", accepted ? "Reboot command sent" : "Reboot command failed");
        return resp;
    }

    private Map<String, Object> rebootAndroid(String siteCode, String ip) {
        String target = ip + ":" + adbPort;
        List<String> connectCmd = Arrays.asList(adbPath, "connect", target);
        List<String> rebootCmd = Arrays.asList(adbPath, "-s", target, "reboot");
        List<String> disconnectCmd = Arrays.asList(adbPath, "disconnect", target);

        log.info("rebootAndroid site={} target={}", siteCode, target);

        ProcessResult conn = runProcess(connectCmd);
        boolean connected = conn.output != null && conn.output.toLowerCase().contains("connected to");
        if (!connected) {
            return androidResp(false, siteCode, ip,
                    "ADB connect failed: " + (conn.output == null ? "" : conn.output.trim()),
                    conn.output);
        }

        ProcessResult reboot = runProcess(rebootCmd);
        ProcessResult disc = runProcess(disconnectCmd);

        boolean rebootOk = reboot.exitCode == 0;
        String combined = "connect: " + safe(conn.output)
                + "\nreboot: " + safe(reboot.output)
                + "\ndisconnect: " + safe(disc.output);

        return androidResp(rebootOk, siteCode, ip,
                rebootOk ? "Reboot command sent" : "ADB reboot failed",
                combined);
    }

    private Map<String, Object> androidResp(boolean ok, String siteCode, String ip, String message, String output) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("isSuccess", ok);
        resp.put("deviceType", "Android");
        resp.put("siteCode", siteCode);
        resp.put("ip", ip);
        resp.put("message", message);
        resp.put("output", output);
        return resp;
    }

    private Map<String, Object> error(String message) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("isSuccess", false);
        resp.put("message", message);
        return resp;
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private ProcessResult runProcess(List<String> command) {
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

    private static class ProcessResult {
        int exitCode = -1;
        String output = "";
        boolean timedOut = false;
    }
}
