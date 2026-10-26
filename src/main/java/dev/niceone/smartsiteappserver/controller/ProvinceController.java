package dev.niceone.smartsiteappserver.controller;

import dev.niceone.smartsiteappserver.model.Province;
import dev.niceone.smartsiteappserver.repository.ProvinceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/province")
public class ProvinceController {
    @Autowired
    private ProvinceRepository repository;

    @GetMapping("/list")
    List<Province> findByRegion(@RequestParam(name="region", required=false) Long region) {
        if (region == null) {
            return repository.findAll();
        }
        return repository.findByRegionId(region);
    }
}
