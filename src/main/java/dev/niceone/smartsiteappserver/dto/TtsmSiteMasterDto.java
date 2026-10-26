package dev.niceone.smartsiteappserver.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO สำหรับส่งข้อมูล Site Master ให้ TTSM
 * ใช้แทนการที่เราเคย POST ไปหา ttsm_endpoint_update_master
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TtsmSiteMasterDto {
    private String siteCode;
    private String siteName;
    private String siteAddress;
    private String latitude;
    private String longitude;
    private String province;
    private String district;
    private String subDistrict;

    // Emergency Key
    private String emergencyKey;
    private String emergencyKeyUpdatedAt;

    // Site Status
    private String status;
    private String lastUpdated;

    // Site Version
    private String appVersion;
    private String firmwareVersion;
}
