package dev.niceone.smartsiteappserver.repository;

import dev.niceone.smartsiteappserver.model.Province;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProvinceRepository extends JpaRepository<Province, Long> {
    List<Province> findByRegionId(Long region);
    Province findByCode(String code);
    Optional<Province> findByNameTh(String name);

    @Cacheable(value = "provincesCache", key = "#id")
    Optional<Province> findById(Long id);
}
