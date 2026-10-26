package dev.niceone.smartsiteappserver.controller;

import dev.niceone.smartsiteappserver.payload.ApiResponse;
import dev.niceone.smartsiteappserver.payload.request.CabinetLockRequest;
import dev.niceone.smartsiteappserver.payload.request.SiteStatusRequest;
import dev.niceone.smartsiteappserver.service.CabinetService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/v2/cabinet")
@Slf4j
public class CabinetControllerV2 {

    @Autowired
    private CabinetService cabinetService;

    @PostMapping(value = "/lock/")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<?> reactCabinetKeyLock(@Valid @RequestBody CabinetLockRequest cabinetRequest) {
        return cabinetService.keyLock(cabinetRequest);
    }

    @PostMapping(value = "/unlock/")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<?> reactCabinetKeyUnLock(@Valid @RequestBody CabinetLockRequest cabinetRequest) {
        return cabinetService.keyUnlock(cabinetRequest);
    }

    @PostMapping(value = "/aux_on/")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<?> reactCabinetAuxOn(@Valid @RequestBody CabinetLockRequest cabinetRequest) {
        return cabinetService.auxOnOff(cabinetRequest, true);
    }

    @PostMapping(value = "/aux_off/")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<?> reactCabinetAuxOff(@Valid @RequestBody CabinetLockRequest cabinetRequest) {
        return cabinetService.auxOnOff(cabinetRequest, false);
    }

}
