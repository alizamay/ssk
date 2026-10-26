package dev.niceone.smartsiteappserver.controller;

import dev.niceone.smartsiteappserver.model.constant.StatusCodeEnum;
import dev.niceone.smartsiteappserver.payload.StatusResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/status")
public class StatusController {

    // Get all
    @GetMapping("/list")
    List<StatusResponse> findAll() {
        List<StatusResponse> responses = Arrays.stream(StatusCodeEnum.values()).map(status -> {
            StatusResponse response = new StatusResponse();
            response.setId(status.id);
            response.setCode(status.code);
            response.setName(status.name);
            return response;
        }).collect(Collectors.toList());

        return responses;
    }
}