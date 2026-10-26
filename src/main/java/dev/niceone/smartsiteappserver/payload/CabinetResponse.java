package dev.niceone.smartsiteappserver.payload;

import dev.niceone.smartsiteappserver.model.constant.LockEventCodeEnum;
import dev.niceone.smartsiteappserver.payload.response.AuxResponse;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
public class CabinetResponse {
    private Long id;
    private String cabinet_code;
    private int cabinet_no;
    private boolean online;
    private String lockEventCode;
    private String doorEventCode;
    private Boolean active;
    private List<DeviceResponse> devices;
    private List<AuxResponse> aux_devices;
    private Boolean auxin1;
    private Boolean auxin2;
    private Boolean auxin3;
    private Boolean auxout;
}
