package dev.niceone.smartsiteappserver.controller;

import dev.niceone.smartsiteappserver.model.Region;
import dev.niceone.smartsiteappserver.model.Role;
import dev.niceone.smartsiteappserver.repository.RegionRepository;
import dev.niceone.smartsiteappserver.repository.RoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/role")
public class RoleController {
    @Autowired
    private RoleRepository roleRepository;

    // Get all
    @GetMapping("/list")
    List<Role> findAll() {
        return roleRepository.findAll();
    }
}
