package dev.niceone.smartsiteappserver.payload;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.ToString;

import javax.validation.constraints.NotNull;
import java.util.Optional;

@Getter
@ToString
@Data
public class DeviceRequest {
    @NotNull
    private Long id;
    private String code;

    @Getter(AccessLevel.NONE)
    private Boolean installed;

    @Getter(AccessLevel.NONE)
    private Boolean enabled;

    public Boolean isInstalled() {
        if (installed == null) {
            return false;
        }
        return installed;
    }

    public Boolean isEnabled() {
        if (enabled == null) {
            return false;
        }
        return enabled;
    }
}
