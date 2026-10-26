package dev.niceone.smartsiteappserver.model;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.elasticsearch.annotations.Document;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Getter
@Setter
@Data
@Document(indexName = "activity")
public class ActivityLog extends AuditModel implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator="activity_seq_gen")
    @SequenceGenerator(name="activity_seq_gen", sequenceName="activity_id_seq", allocationSize=1)
    private Long id;

    private String title;
    private String type;  // USER, SITE
    private String message;
    private String level;  // INFO, DEBUG, WARNING, ERROR
    private String status;

    private LocalDateTime insert_date;

    public ActivityLog() {
    }

    public ActivityLog(String title, String type, String message, String level, String status) {
        this.title = title;
        this.type = type;
        this.message = message;
        this.level = level;
        this.status = status;
        this.insert_date = LocalDateTime.now();
    }

    /*
     * "Web GUI Login Attempt"
     * "Web GUI Login Result"
     *
     * "Ask TTSM for on-site status"
     * "Init check site status"
     * "Send to CFMS"
     * "Finished check site status"
     * "Init emergency key timer"
     * "Send emergency key to site"
     * "Init check sensor status"
     * */
}