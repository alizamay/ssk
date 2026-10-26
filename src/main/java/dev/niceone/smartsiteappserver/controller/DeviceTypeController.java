package dev.niceone.smartsiteappserver.controller;

import dev.niceone.smartsiteappserver.model.constant.DeviceTypeCodeEnum;
import dev.niceone.smartsiteappserver.payload.DeviceResponse;
import dev.niceone.smartsiteappserver.payload.DeviceTypeResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/device_type")
public class DeviceTypeController {

    // Get all
    @GetMapping("/list")
    List<DeviceTypeResponse> findAll() {
        List<DeviceTypeResponse> deviceTypeResponse = Arrays.stream(DeviceTypeCodeEnum.values())
                .filter(deviceTypeCodeEnum -> deviceTypeCodeEnum.isActive()).map(deviceType -> {
            DeviceTypeResponse deviceTypeRes = new DeviceTypeResponse();
            deviceTypeRes.setId(deviceType.id);
            deviceTypeRes.setCode(deviceType.code);
            deviceTypeRes.setName(deviceType.name);
            return deviceTypeRes;
        }).collect(Collectors.toList());

        return deviceTypeResponse;
    }
}
