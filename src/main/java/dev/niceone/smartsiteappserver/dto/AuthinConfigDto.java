package dev.niceone.smartsiteappserver.dto;

import lombok.Data;

@Data
public class AuthinConfigDto {
    private Boolean isDisabled;
    private Integer dailyLimit;
}

