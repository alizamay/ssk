package dev.niceone.smartsiteappserver.controller;

import dev.niceone.smartsiteappserver.model.Province;
import dev.niceone.smartsiteappserver.model.SubDistrict;
import dev.niceone.smartsiteappserver.repository.ProvinceRepository;
import dev.niceone.smartsiteappserver.repository.SubDistrictRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/sub-district")
public class SubDistrictController {
    @Autowired
    private SubDistrictRepository repository;

    @GetMapping("/list")
    List<SubDistrict> findByDistrict(@RequestParam(name="district", required=false) Long district) {
        if (district == null) {
            return repository.findAll();
        }
        return repository.findByDistrictId(district);
    }
}
