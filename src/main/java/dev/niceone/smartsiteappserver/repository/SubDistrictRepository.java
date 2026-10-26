package dev.niceone.smartsiteappserver.repository;

import dev.niceone.smartsiteappserver.model.SubDistrict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubDistrictRepository extends JpaRepository<SubDistrict, Long> {
    List<SubDistrict> findByDistrictId(Long district);
    SubDistrict findByNameEn(String nameEn);

    @Cacheable(value = "subDistrictsCache", key = "#id")
    Optional<SubDistrict> findById(Long id);
}
