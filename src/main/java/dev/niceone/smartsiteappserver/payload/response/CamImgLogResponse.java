package dev.niceone.smartsiteappserver.payload.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CamImgLogResponse {
    private Long id;
    private String url;
    private String fileName;
    private LocalDateTime createdAt;
}
