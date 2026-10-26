package dev.niceone.smartsiteappserver.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "common_log", schema = "logdb")
@Getter
@Setter
public class CommonLog extends AuditModel implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator="commonlog_seq_gen")
    @SequenceGenerator(name="commonlog_seq_gen", sequenceName="commonlog_id_seq", allocationSize=1)
    private Long id;

    private LocalDateTime insert_date;
    private String message;

    public CommonLog() {
    }

    public CommonLog(String message) {
        this.message = message;
    }
}
