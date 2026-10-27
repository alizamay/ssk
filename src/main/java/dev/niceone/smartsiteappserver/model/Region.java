package dev.niceone.smartsiteappserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import javax.persistence.*;
import javax.validation.constraints.NotEmpty;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter @Setter
@Table(name = "region")
public class Region extends AuditModel implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator="region_seq_gen")
    @SequenceGenerator(name="region_seq_gen", sequenceName="region_id_seq", allocationSize=1)
    private Long id;

    @NotEmpty(message = "Please provide region code")
//    @JsonSerialize
//    @JsonProperty("value")
    private String code;

    @NotEmpty(message = "Please provide a name")
//    @JsonSerialize
//    @JsonProperty("label")
    private String name;

    @Column(columnDefinition="boolean default true")
    private Boolean active;

    @OneToMany(
            mappedBy = "region",
            cascade = CascadeType.ALL,
            // LAZY: EAGER pulled every site of the region (thousands of rows) whenever a Region was
            // loaded, e.g. for each user's regions on login and on every alarm/event list request.
            fetch = FetchType.LAZY,
            orphanRemoval = true
    )
    @Fetch(FetchMode.SELECT)
    @JsonIgnore
    private List<Site> sites = new ArrayList<>();

    public Region() {
    }

    public Region(String code, String name) {
        this.code = code;
        this.name = name;
        this.active = true;
    }

    public Long getId() {
        return id;
    }
}
