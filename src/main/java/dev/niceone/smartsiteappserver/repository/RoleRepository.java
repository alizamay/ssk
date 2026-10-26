package dev.niceone.smartsiteappserver.repository;

import dev.niceone.smartsiteappserver.model.Role;
import dev.niceone.smartsiteappserver.model.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByName(RoleName roleName);
}