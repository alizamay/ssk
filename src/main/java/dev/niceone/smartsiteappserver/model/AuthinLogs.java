package dev.niceone.smartsiteappserver.model;

import lombok.*;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "authin_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthinLogs {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "\"timestamp\"", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "site_code", nullable = false)
    private String siteCode;

    @Column(name = "url")
    private String url;

    @Column(name = "message")
    private String message;

    @Column(name = "is_clear")
    private Boolean isClear = false;
}