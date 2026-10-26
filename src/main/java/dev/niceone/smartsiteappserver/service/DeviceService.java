package dev.niceone.smartsiteappserver.service;

import dev.niceone.smartsiteappserver.exception.ResourceNotFoundException;
import dev.niceone.smartsiteappserver.model.Device;
import dev.niceone.smartsiteappserver.payload.ApiResponse;
import dev.niceone.smartsiteappserver.payload.DeviceRequest;
import dev.niceone.smartsiteappserver.repository.DeviceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class DeviceService {

    @Autowired
    private DeviceRepository deviceRepository;

    public ResponseEntity<ApiResponse> updateDevice(Long id, DeviceRequest deviceRequest) {
        Device device = deviceRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Device", "id", id));

        deviceRepository.save(device);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse(true, "UPDATE OK"));
    }
//    Flux<MyModel> tweetFlux = WebClient.create()
//        .get()
//        .uri("http://str_url")
//        .retrieve()
//        .bodyToFlux(MyModel.class);
}
