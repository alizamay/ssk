package dev.niceone.smartsiteappserver.repository;

import dev.niceone.smartsiteappserver.model.AuxDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuxDeviceRepository extends JpaRepository<AuxDevice, Long>, JpaSpecificationExecutor<AuxDevice> {
//    List<AuxDevice> findByActive(boolean active);

//    List<AuxDevice> findByCabinetInAndActiveIsTrue(List<Cabinet> cabinets);
//
//    List<AuxDevice> findByCabinetAndActiveIsTrue(Cabinet cabinet);
//
//    List<AuxDevice> findByCabinetAndDeviceTypeCodeAndActiveIsTrue(Cabinet cabinet, DeviceTypeCodeEnum deviceType);
//    AuxDevice findByCabinetAndDeviceTypeCode(Cabinet cabinet, DeviceTypeCodeEnum deviceType);
}