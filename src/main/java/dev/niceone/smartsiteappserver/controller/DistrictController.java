package dev.niceone.smartsiteappserver.controller;

import dev.niceone.smartsiteappserver.model.District;
import dev.niceone.smartsiteappserver.model.Province;
import dev.niceone.smartsiteappserver.repository.DistrictRepository;
import dev.niceone.smartsiteappserver.repository.ProvinceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/district")
public class DistrictController {
    @Autowired
    private DistrictRepository repository;

    @GetMapping("/list")
    List<District> findByProvince(@RequestParam(name="province", required=false) Long province) {
        if (province == null) {
            return repository.findAll();
        }
        return repository.findByProvinceId(province);
    }
}
