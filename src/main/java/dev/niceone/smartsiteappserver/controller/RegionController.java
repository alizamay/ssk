package dev.niceone.smartsiteappserver.controller;

import dev.niceone.smartsiteappserver.exception.ResourceNotFoundException;
import dev.niceone.smartsiteappserver.model.Region;
import dev.niceone.smartsiteappserver.model.Site;
import dev.niceone.smartsiteappserver.payload.RegionResponse;
import dev.niceone.smartsiteappserver.repository.RegionRepository;
import dev.niceone.smartsiteappserver.service.RegionService;
import dev.niceone.smartsiteappserver.service.SiteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.Min;
import java.util.List;

@RestController
@RequestMapping("/api/region")
public class RegionController {

    @Autowired
    private RegionRepository repository;

    @Autowired
    private RegionService regionService;

    // Get all
    @GetMapping("/list")
    List<Region> findAll() {
        return repository.findAll();
    }

    @GetMapping("/v2/list")
    List<RegionResponse> listRegion() {
        return regionService.listRegion();
    }
}
