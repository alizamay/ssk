package dev.niceone.smartsiteappserver.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO สำหรับรับข้อมูล Job Status จาก TTSM
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TtsmJobStatusDto {
    private List<String> siteCode;   // array ของ site codes
    private String jobId;
    private String jobStatus;        // "On-Site", "Done", "Held"
    private String actionDate;       // format: "yyyy-MM-dd HH:mm:ss"
}
