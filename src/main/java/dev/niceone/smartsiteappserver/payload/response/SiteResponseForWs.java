package dev.niceone.smartsiteappserver.payload.response;

import dev.niceone.smartsiteappserver.payload.CabinetResponse;
import dev.niceone.smartsiteappserver.payload.StatusResponse;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Setter
@Getter
public class SiteResponseForWs {
    private Long id;
    private String code;
    private String name_th;
    private String name_en;

    private StatusResponse status;

    private String site_key;

    private LocalDateTime installed_date;
    private Boolean isInstall;
    private LocalDateTime updatedAt;

    private Boolean active;
    private String lat;
    private String lon;
    private String ip_address;
    private int port;
    private String subnet_mask;
    private String default_gateway;

    private int cabinet_count;
    private List<CabinetResponse> cabinets;
}
