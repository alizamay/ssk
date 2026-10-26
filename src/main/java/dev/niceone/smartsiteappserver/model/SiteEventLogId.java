package dev.niceone.smartsiteappserver.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SiteEventLogId implements Serializable {

    private UUID id;               // ชื่อฟิลด์ตรงกับใน Entity @Id field แรก
    private LocalDateTime createDate; // ชื่อฟิลด์ตรงกับใน Entity @Id field ที่สอง

    // ต้อง implements Serializable และ override equals, hashCode (Lombok @Data ช่วยได้)
}
