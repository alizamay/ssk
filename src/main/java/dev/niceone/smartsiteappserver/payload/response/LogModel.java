package dev.niceone.smartsiteappserver.payload.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class LogModel {
    private UUID id;
    private boolean active;
    private int alarm_type;
    private String cabinet_id;
    private String device_id;
    private String resolve_date;
    private String site_id;
    private String create_date;
    private boolean write_to_file;

    private String alarm_type_code;
    private String alarm_type_name;
    private String cabinet_no;
    private String cabinet_code;
    private int lock_event_id;
    private String lock_event_name;
    private int door_event_id;
    private String door_event_name;
    private String site_code;
    private String site_name_th;
    private String site_name_en;
    private String status_code;
    private String region_code;
    private String region_name;
    private String update_date;

    private int count;
    private String timestamps;

    public LogModel() {

    }

    public LogModel(LogModel other) {
        this.id = other.id != null ? UUID.fromString(other.id.toString()) : null; // ป้องกัน shared UUID object
        this.active = other.active;
        this.alarm_type = other.alarm_type;
        this.cabinet_id = other.cabinet_id;
        this.device_id = other.device_id;
        this.resolve_date = other.resolve_date;
        this.site_id = other.site_id;
        this.create_date = other.create_date;
        this.write_to_file = other.write_to_file;

        this.alarm_type_code = other.alarm_type_code;
        this.alarm_type_name = other.alarm_type_name;
        this.cabinet_no = other.cabinet_no;
        this.cabinet_code = other.cabinet_code;
        this.lock_event_id = other.lock_event_id;
        this.lock_event_name = other.lock_event_name;
        this.door_event_id = other.door_event_id;
        this.door_event_name = other.door_event_name;
        this.site_code = other.site_code;
        this.site_name_th = other.site_name_th;
        this.site_name_en = other.site_name_en;
        this.status_code = other.status_code;
        this.region_code = other.region_code;
        this.region_name = other.region_name;
        this.update_date = other.update_date;

        this.count = other.count;
        this.timestamps = other.timestamps;
    }
}
