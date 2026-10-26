package dev.niceone.smartsiteappserver.controller;

import dev.niceone.smartsiteappserver.model.Device;
import dev.niceone.smartsiteappserver.payload.DeviceRequest;
import dev.niceone.smartsiteappserver.repository.DeviceRepository;
import dev.niceone.smartsiteappserver.service.DeviceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/device")
@Slf4j
public class DeviceController {
    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private DeviceService deviceService;

    @GetMapping("/list")
    List<Device> findAll() {
        return deviceRepository.findByActive(true);
    }

    // Update
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<?> updateDevice(@Valid @RequestBody DeviceRequest deviceRequest, @PathVariable Long id) {
        return deviceService.updateDevice(id, deviceRequest);
    }
}
