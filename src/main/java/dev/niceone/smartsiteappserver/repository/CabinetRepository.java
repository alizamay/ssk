package dev.niceone.smartsiteappserver.repository;

import dev.niceone.smartsiteappserver.model.Cabinet;
import dev.niceone.smartsiteappserver.model.Region;
import dev.niceone.smartsiteappserver.model.Site;
import dev.niceone.smartsiteappserver.model.constant.LockEventCodeEnum;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CabinetRepository extends JpaRepository<Cabinet, Long> {
    List<Cabinet> findByIdIn(List<Long> ids);

    List<Cabinet> findByCodeIn(List<String> codes);

    List<Cabinet> findByIdInAndActiveIsTrue(List<Long> ids);
    List<Cabinet> findBySiteAndActiveIsTrue(Site site);
    List<Cabinet> findBySite(Site site);
    Cabinet findByCode(String code);
    Cabinet findBySiteAndCode(Site site, String code);
    Cabinet findBySiteAndCabinetNo(Site site, Integer no);

    int countBySiteInAndActiveIsTrue(List<Site> sites);
    int countBySiteInAndOnlineAndActiveIsTrue(List<Site> sites, boolean online);
    int countBySiteInAndLockEventCodeAndActiveIsTrue(List<Site> sites, LockEventCodeEnum lockEventCode);
//    int countBySiteAndActive(Site site, boolean active);
//    int countBySiteAndOnlineAndActive(Site site, boolean online, boolean active);
//    int countBySiteAndLockEventCodeAndActive(Site site, LockEventCodeEnum lockEventCode, boolean active);

//    @Query(value = "select * " +
//            "from cabinet as c " +
//            "inner join device as d on c.id= d.cabinet_id " +
//            "where  c.code in :codes", nativeQuery = true)
}
