package dev.niceone.smartsiteappserver.payload;

import dev.niceone.smartsiteappserver.payload.request.AuxRequest;
import lombok.Getter;
import lombok.ToString;
import java.util.List;

@Getter
@ToString
public class CabinetRequest {
    private Long id;
    private String cabinet_code;
    private int cabinet_no;
    private Boolean online;
    private String doorEventCode;
    private String lockEventCode;
    private Boolean active;
    private List<DeviceRequest> devices;
    private List<AuxRequest> aux_devices;
}
