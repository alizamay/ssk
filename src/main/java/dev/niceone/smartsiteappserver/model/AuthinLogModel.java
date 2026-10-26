package dev.niceone.smartsiteappserver.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;

@Getter
@Setter
@Builder
public class AuthinLogModel {
    private String id;
    private ZonedDateTime timestamp;
    private String site_code;
    private String url;
    private String message;
    private Boolean is_clear;
}
