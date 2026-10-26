package dev.niceone.smartsiteappserver.model;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class OnlineStatusDetailResponse {
    private Long id;
    private String code;
    private String nameTh;
    private String nameEn;
    private String ipAddress;
    private String defaultGateway;
    private String subnetMask;
    private Integer port;
    private String siteKey;
    private Integer status;
    private Boolean active;
    private Boolean isInstall;
    private LocalDateTime installedDate;
    private String lat;
    private String lon;
    private String contactPointName;
    private String contactPointTel;
    private String address;
    private Boolean keyGenerating;
    private LocalDateTime lastKeyGenerate;
    private LocalDateTime lastKeyUpdate;
    private LocalDateTime reportStatusAt;
    private Boolean emergKeySuccess;
    private String version;
    private Integer motion;
    private Integer firmwareOption;
    private String region;
    private String province;
    private LocalDateTime checkTime;
    private Boolean onlineStatus;
    private Boolean isPingIp;
    private Boolean isPingGateway;
    private Boolean isCallHelpcheck;
    private Boolean statusPingIp;
    private Boolean statusPingGateway;
    private Boolean statusCallHelpcheck;
    private String jsonResult;
}
