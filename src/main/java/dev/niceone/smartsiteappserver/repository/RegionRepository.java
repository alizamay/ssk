package dev.niceone.smartsiteappserver.repository;

import dev.niceone.smartsiteappserver.model.Region;
import dev.niceone.smartsiteappserver.model.Site;
import dev.niceone.smartsiteappserver.model.SubDistrict;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RegionRepository extends JpaRepository<Region, Long> , JpaSpecificationExecutor<Region> {
    List<Region> findByActive(Boolean active);
    Region findByCode(String code);
}
