package dev.niceone.smartsiteappserver.repository;

import dev.niceone.smartsiteappserver.model.AlarmLog;
import dev.niceone.smartsiteappserver.model.Cabinet;
import dev.niceone.smartsiteappserver.model.Device;
import dev.niceone.smartsiteappserver.model.Site;
import dev.niceone.smartsiteappserver.model.constant.AlarmTypeCodeEnum;
import dev.niceone.smartsiteappserver.model.constant.LockEventCodeEnum;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

@Repository
public interface AlarmLogRepository extends JpaRepository<AlarmLog, Long> , JpaSpecificationExecutor<AlarmLog> {
    List<AlarmLog> findByIdIn(List<Long> ids);
    List<AlarmLog> findByDeviceAndAlarmTypeAndResolveDateIsNull(Device device, AlarmTypeCodeEnum alarmType);
    List<AlarmLog> findByCabinetAndAlarmTypeAndResolveDateIsNull(Cabinet cabinet, AlarmTypeCodeEnum alarmType);
    List<AlarmLog> findBySiteAndAlarmTypeAndResolveDateIsNull(Site site, AlarmTypeCodeEnum alarmType);

    List<AlarmLog> findAllByWriteToFileIsFalse();
    List<AlarmLog> findAllByAlarmTypeAndResolveDateIsNull(AlarmTypeCodeEnum alarmType);
    List<AlarmLog> findAllByAlarmTypeAndResolveDateIsNotNull(AlarmTypeCodeEnum alarmType);

    int countBySiteInAndResolveDateAndActive(List<Site> site, LocalDateTime resolveDate, boolean active);
//    int countBySiteAndResolveDateAndActive(Site site, LocalDateTime resolveDate, boolean active);
}
