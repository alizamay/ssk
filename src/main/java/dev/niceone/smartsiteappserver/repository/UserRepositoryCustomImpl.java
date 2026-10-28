package dev.niceone.smartsiteappserver.repository;

import dev.niceone.smartsiteappserver.model.constant.LockEventCodeEnum;
import dev.niceone.smartsiteappserver.model.constant.StatusCodeEnum;
import dev.niceone.smartsiteappserver.payload.MyHomeResponse;
import dev.niceone.smartsiteappserver.payload.MyRegionResponse;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class UserRepositoryCustomImpl implements UserRepositoryCustom {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Override
    public MyHomeResponse getMyHomeDataOptimized(Long userId) {
        // Single query to get all region statistics
        String sql = "SELECT " +
            "r.id as region_id, " +
            "r.name as region_name, " +
            "COUNT(DISTINCT s.id) as count_site, " +
            "COUNT(DISTINCT al.site_id) as count_site_active_alarm, " +
            "COUNT(DISTINCT c.id) as count_cabinet, " +
            "COUNT(DISTINCT CASE WHEN s.status = :statusUp THEN c.id END) as count_cabinet_online, " +
            "COUNT(DISTINCT CASE WHEN s.status = :statusDown THEN c.id END) as count_cabinet_offline, " +
            "COUNT(DISTINCT CASE WHEN c.lock_event_code = :lockLocked THEN c.id END) as count_cabinet_locked, " +
            "COUNT(DISTINCT CASE WHEN c.lock_event_code = :lockUnlocked THEN c.id END) as count_cabinet_unlocked, " +
            "COUNT(DISTINCT CASE WHEN c.lock_event_code = :lockUnknown THEN c.id END) as count_cabinet_unknown " +
            "FROM smartsite.region r " +
            "INNER JOIN smartsite.user_regions ur ON r.id = ur.region_id " +
            "LEFT JOIN smartsite.site s ON r.id = s.region_id AND s.active = true " +
            "LEFT JOIN smartsite.cabinet c ON s.id = c.site_id AND c.active = true " +
            // Alarms moved from alarm_log (no longer written) to site_event_log: one row per
            // site, alarm type and cabinet per day, reopened by is_resolve. Only today's rows
            // are ever resolved, so older unresolved rows are not counted.
            "LEFT JOIN (SELECT DISTINCT site_id FROM smartsite.site_event_log " +
            "           WHERE active = true AND is_resolve = false AND create_date >= current_date) al " +
            "       ON al.site_id = CAST(s.id AS text) " +
            "WHERE ur.user_id = :userId AND r.active = true " +
            "GROUP BY r.id, r.name " +
            "ORDER BY r.name";
        
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("userId", userId);
        query.setParameter("statusUp", StatusCodeEnum.STATUS_UP.getId());
        query.setParameter("statusDown", StatusCodeEnum.STATUS_DOWN.getId());
        query.setParameter("lockLocked", LockEventCodeEnum.LOCK_EVENT_LOCK.getId());
        query.setParameter("lockUnlocked", LockEventCodeEnum.LOCK_EVENT_UNLOCK.getId());
        query.setParameter("lockUnknown", LockEventCodeEnum.LOCK_EVENT_UNKNOW.getId());
        
        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();
        
        List<MyRegionResponse> myRegionResponseList = new ArrayList<>();
        int countAllSite = 0;
        int countAllCabinet = 0;
        int countAllActiveAlarm = 0;
        
        for (Object[] row : results) {
            MyRegionResponse myRegionResponse = new MyRegionResponse();
            myRegionResponse.setId(((BigInteger) row[0]).longValue());
            myRegionResponse.setName((String) row[1]);
            
            int countSite = ((BigInteger) row[2]).intValue();
            int countSiteActiveAlarm = ((BigInteger) row[3]).intValue();
            int countCabinet = ((BigInteger) row[4]).intValue();
            int countCabinetOnline = ((BigInteger) row[5]).intValue();
            int countCabinetOffline = ((BigInteger) row[6]).intValue();
            int countCabinetLocked = ((BigInteger) row[7]).intValue();
            int countCabinetUnlocked = ((BigInteger) row[8]).intValue();
            int countCabinetUnknown = ((BigInteger) row[9]).intValue();
            
            myRegionResponse.setCountSite(countSite);
            myRegionResponse.setCountSiteActiveAlarm(countSiteActiveAlarm);
            myRegionResponse.setCountCabinet(countCabinet);
            myRegionResponse.setCountCabinetOnline(countCabinetOnline);
            myRegionResponse.setCountCabinetOffline(countCabinetOffline);
            myRegionResponse.setCountCabinetLocked(countCabinetLocked);
            myRegionResponse.setCountCabinetUnlocked(countCabinetUnlocked);
            myRegionResponse.setCountCabinetUnknow(countCabinetUnknown);
            
            myRegionResponseList.add(myRegionResponse);
            
            countAllSite += countSite;
            countAllCabinet += countCabinet;
            countAllActiveAlarm += countSiteActiveAlarm;
        }
        
        MyHomeResponse myHomeResponse = new MyHomeResponse();
        myHomeResponse.setCountAllSite(countAllSite);
        myHomeResponse.setCountAllCabinet(countAllCabinet);
        myHomeResponse.setCountAllActiveAlarm(countAllActiveAlarm);
        myHomeResponse.setMyRegionResponseList(myRegionResponseList);
        
        return myHomeResponse;
    }
}