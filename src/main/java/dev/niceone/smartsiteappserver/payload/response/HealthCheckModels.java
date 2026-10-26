package dev.niceone.smartsiteappserver.payload.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class HealthCheckModels {
    private String device_type;
    private String smartLock;
    private int slaveNum;
    private Map<String, String> slaveStatus;
    private Map<String, String> slaveSmartLock;
    private String temperature;
    private float temperatureValue;
    private float temperatureMaxValue;
    private String motion;
    private String camera;
    private String vibration;
    private String fan_status;
    private String doorSensor;
    private String sdcard;
    private String sdcard_Space;
    private String sdcard_write_speed;
    private String sdcard_read_speed;
}
