package dev.niceone.smartsiteappserver.dto;

import lombok.Data;

@Data
public class AuthinLogDto {
    private String siteCode;
    private String url;
    private String message;
    private Boolean isClear;
}
