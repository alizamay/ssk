package dev.niceone.smartsiteappserver.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import javax.persistence.*;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

//@Entity
//@Getter @Setter
//@Table(name = "status")
//public class Status_Deprecated extends AuditModel implements Serializable {
//
//    @Id
//    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator="status_seq_gen")
//    @SequenceGenerator(name="status_seq_gen", sequenceName="status_id_seq", allocationSize=1)
//    private Long id;
//
//    @NotNull
//    private StatusCode code;
//
//    @NotEmpty(message = "Please provide name")
//    private String name;
//
//    @Column(columnDefinition="boolean default true")
//    private Boolean active = true;
//
//    public Status_Deprecated() {
//    }
//
//    public Status_Deprecated(StatusCode code, String name) {
//        this.code = code;
//        this.name = name;
//        this.active = true;
//    }
//}