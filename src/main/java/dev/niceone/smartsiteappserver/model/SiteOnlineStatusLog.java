package dev.niceone.smartsiteappserver.model;

import javax.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.LocalTime;


@Entity
@Table(name = "site_online_status_log", schema = "smartsite")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SiteOnlineStatusLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "site_code", nullable = false)
    private String siteCode;

    @Column(name = "status", nullable = false)
    private Boolean status;

    @Column(name = "period", nullable = false)
    private LocalTime period; // เช่น 08:00 หรือ 20:00

    @Column(name = "check_time", nullable = false)
    private LocalDateTime checkTime;
    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "default_gateway")
    private String defaultGateway;

    @Column(name = "status_ping_ip")
    private Boolean statusPingIp;

    @Column(name = "status_ping_gateway")
    private Boolean statusPingGateway;

    @Column(name = "status_call_helpcheck")
    private Boolean statusCallHelpcheck;

    @Column(name = "is_ping_ip")
    private Boolean isPingIp;

    @Column(name = "is_ping_gateway")
    private Boolean isPingGateway;

    @Column(name = "is_call_helpcheck")
    private Boolean isCallHelpcheck;

    @Column(name = "json_result", columnDefinition = "TEXT")
    private String jsonResult;

    @Column(name = "port")
    private Integer port;
}
