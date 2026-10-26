package dev.niceone.smartsiteappserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.niceone.smartsiteappserver.repository.RoleRepository;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.persistence.*;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.HashSet;
import java.util.Set;

@Entity
@Getter @Setter
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(columnNames = {
                "username"
        }),
        @UniqueConstraint(columnNames = {
                "email"
        })
})
public class User extends AuditModel {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator="user_seq_gen")
    @SequenceGenerator(name="user_seq_gen", sequenceName="user_id_seq", allocationSize=1)
    private Long id;

    @NotBlank
    @Size(max = 15)
    private String username;

    @NotBlank
    @Size(max = 40)
    private String firstname;

    @Size(max = 40)
    private String lastname;

    @NaturalId
    @Size(max = 40)
    @Email
    private String email;

    @Size(max = 100)
    @JsonIgnore
    private String password;

    @Column(columnDefinition="boolean default true")
    private Boolean active;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_sites",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "site_id"))
    private Set<Site> sites = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_regions",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "region_id"))
    private Set<Region> regions = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    public User() {
    }

    private PasswordEncoder mockPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    public User(String username, String password, String firstname, String lastname, String email) {
        this.username = username;
        this.password = mockPasswordEncoder().encode(password);
        this.firstname = firstname;
        this.lastname = lastname;
        this.email = email;

        this.active = true;
    }

    public User(String username, String password, String firstname, String lastname) {
        this.username = username;
        this.password = mockPasswordEncoder().encode(password);
        this.firstname = firstname;
        this.lastname = lastname;

        this.active = true;
    }
}