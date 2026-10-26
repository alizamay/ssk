package dev.niceone.smartsiteappserver.payload.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
public class DeviceHealthCheckResp {
    private String site_code;
    private String cabinet_code;
    private Boolean is_success;
    private String err_msg;
    private String dt;
    private String serial;
    private HealthCheckModels healthcheck;

    public DeviceHealthCheckResp(String site_code, String cabinet_code, Boolean is_success, String err_msg, String dt, String serial,HealthCheckModels healthcheck) {
        this.site_code = site_code;
        this.cabinet_code = cabinet_code;
        this.is_success = is_success;
        this.err_msg = err_msg;
        this.dt = dt;
        this.serial = serial;
        this.healthcheck = healthcheck;
    }

    public DeviceHealthCheckResp() {
        this.site_code = "";
        this.cabinet_code = "";
        this.is_success = false;
        this.err_msg = "";
        this.dt = "";
        this.serial = "";
        this.healthcheck = new HealthCheckModels();
    }
}



