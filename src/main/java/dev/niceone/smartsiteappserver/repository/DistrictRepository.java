package dev.niceone.smartsiteappserver.repository;

import dev.niceone.smartsiteappserver.model.District;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DistrictRepository extends JpaRepository<District, Long> {
    List<District> findByProvinceId(Long province);

    @Cacheable(value = "districtsCache", key = "#id")
    Optional<District> findById(Long id);

    List<District> findByNameTh(String name);
}