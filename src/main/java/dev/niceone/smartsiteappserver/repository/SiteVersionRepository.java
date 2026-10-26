package dev.niceone.smartsiteappserver.repository;

import dev.niceone.smartsiteappserver.model.Region;
import dev.niceone.smartsiteappserver.model.Site;
import dev.niceone.smartsiteappserver.model.SiteVersion;
import dev.niceone.smartsiteappserver.model.constant.StatusCodeEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SiteVersionRepository extends JpaRepository<SiteVersion, Long> {
    Optional<SiteVersion> findFirstBySiteId(Long siteId);
}
