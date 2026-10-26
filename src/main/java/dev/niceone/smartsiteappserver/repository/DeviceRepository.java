package dev.niceone.smartsiteappserver.repository;

import dev.niceone.smartsiteappserver.model.Cabinet;
import dev.niceone.smartsiteappserver.model.Device;
import dev.niceone.smartsiteappserver.model.constant.DeviceTypeCodeEnum;
import dev.niceone.smartsiteappserver.model.constant.StatusCodeEnum;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Long>, JpaSpecificationExecutor<Device> {
    List<Device> findByActive(boolean active);

//    @Cacheable(value = "devicesCacheByCabinets", key = "#cabinets", unless="#result == null")
    List<Device> findByCabinetInAndActiveIsTrue(List<Cabinet> cabinets);

    List<Device> findByCabinetAndActiveIsTrue(Cabinet cabinet);

    List<Device> findByCabinetAndDeviceTypeCodeAndActiveIsTrue(Cabinet cabinet, DeviceTypeCodeEnum deviceType);
    Device findFirstByCabinetAndDeviceTypeCode(Cabinet cabinet, DeviceTypeCodeEnum deviceType);
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Device SET updated_at = NOW(), event_type = :event_id, status = :status_id WHERE cabinet_id = :cabinet_id and device_type_code = :type_id")
    void updateEventAndStatusQuery(@Param("type_id")  int type_id, @Param("event_id")  int event_id, @Param("status_id") StatusCodeEnum status_id, @Param("cabinet_id")  Long cabinet_id);
}

