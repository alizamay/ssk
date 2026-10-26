package dev.niceone.smartsiteappserver.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.niceone.smartsiteappserver.model.*;
import dev.niceone.smartsiteappserver.model.constant.StatusCodeEnum;
import dev.niceone.smartsiteappserver.payload.CabinetResponse;
import dev.niceone.smartsiteappserver.payload.DeviceResponse;
import dev.niceone.smartsiteappserver.payload.SiteResponse;
import dev.niceone.smartsiteappserver.payload.StatusResponse;
import dev.niceone.smartsiteappserver.payload.request.ReportDeviceRequest;
import dev.niceone.smartsiteappserver.payload.response.AuxResponse;
import dev.niceone.smartsiteappserver.payload.response.SiteResponseForWs;
import dev.niceone.smartsiteappserver.repository.DistrictRepository;
import dev.niceone.smartsiteappserver.repository.ProvinceRepository;
import dev.niceone.smartsiteappserver.repository.SiteVersionRepository;
import dev.niceone.smartsiteappserver.repository.SubDistrictRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class SiteModelMapper {

    private static StatusResponse getStatusResponse(StatusCodeEnum status) {
        StatusResponse statusResponse = new StatusResponse();
        statusResponse.setId(status.getId());
        statusResponse.setCode(status.getCode());
        statusResponse.setName(status.getName());
//        statusResponse.setActive(status.getActive());
        return statusResponse;
    }

    private static List<AuxResponse> genAuxDeviceResponse(Cabinet cabinet) {
        List<AuxResponse> auxResponses = cabinet.getAux_devices().stream()
                .map(aux -> {
            AuxResponse auxResponse = new AuxResponse();
            auxResponse.setId(aux.getId());
            auxResponse.setAux_no(aux.getAuxNo());
            auxResponse.setAux_name(aux.getName());
            auxResponse.setIs_input(aux.getIs_input());
            auxResponse.setIs_on(aux.getIs_on());
            auxResponse.setIs_active(aux.getIs_active());
            return auxResponse;
        }).collect(Collectors.toList());
        return auxResponses;
    }

    private static List<DeviceResponse> genDeviceResponse(Cabinet cabinet) throws JsonProcessingException {
        Stream<Device> devices = cabinet.getDevices().stream().sorted(Comparator.comparing(Device::getDeviceTypeCode));


        ObjectMapper mapper = new ObjectMapper();


        List<DeviceResponse> deviceResponses = devices.map(device -> {


            DeviceResponse deviceRespone = new DeviceResponse();
            deviceRespone.setId(device.getId());
            deviceRespone.setInstalled_date(device.getInstalled_date());
            deviceRespone.setIs_install(device.getIs_install());
            deviceRespone.setIs_enable(device.getIs_enable());
            deviceRespone.setActive(device.getActive());

            deviceRespone.setType_id(device.getDeviceTypeCode().id);
            deviceRespone.setType_code(device.getDeviceTypeCode().code);
            deviceRespone.setType_name(device.getDeviceTypeCode().name);

            try {
                ReportDeviceRequest[] device_cab = mapper.readValue(cabinet.getJsonDevices(), ReportDeviceRequest[].class);
                ReportDeviceRequest d_cab = Arrays.stream(device_cab).filter(x ->x.getDevice_type_id() == device.getDeviceTypeCode().id).findFirst().orElse(null);
                if(d_cab != null){
                    StatusCodeEnum eStatus = StatusCodeEnum.getById(Integer.valueOf(d_cab.getDevice_status()));
                    deviceRespone.setStatus(getStatusResponse(eStatus));
                }else {
                    deviceRespone.setStatus(getStatusResponse(device.getStatus()));
                }
            }catch (Exception ex){
                deviceRespone.setStatus(getStatusResponse(device.getStatus()));
            }



            return deviceRespone;
        }).collect(Collectors.toList());
//        System.out.println("deviceResponses: "+deviceResponses);
        return deviceResponses;
    }

    public static SiteResponse mapSiteToSiteResponse(Site site,
                                                     ProvinceRepository provinceRepository,
                                                     DistrictRepository districtRepository,
                                                     SubDistrictRepository subDistrictRepository,
                                                     SiteVersionRepository siteVersionRepository) {
        SiteResponse siteResponse = new SiteResponse();
        siteResponse.setId(site.getId());
        siteResponse.setCode(site.getCode());
        siteResponse.setName_th(site.getName_th());
        siteResponse.setName_en(site.getName_en());

        siteResponse.setStatus(getStatusResponse(site.getStatus()));

        siteResponse.setRegion_id(site.getRegion().getId());
        siteResponse.setRegion_code(site.getRegion().getCode());
        siteResponse.setRegion_name(site.getRegion().getName());

        try {
            if (site.getProvince() != null && site.getProvince().getId() != null) {
                Province province = site.getProvince();
                siteResponse.setProvince_id(province.getId());
                siteResponse.setProvince_name_en(province.getNameEn());
                siteResponse.setProvince_name_th(province.getNameTh());
                siteResponse.setProvince_code(province.getCode());
            }
        } catch(Exception e) {
            log.info("got Province null from "+site.getCode());
        }

        try {
            if (site.getDistrict() != null && site.getDistrict().getId() != null) {
                District district = site.getDistrict();
                siteResponse.setDistrict_id(district.getId());
                siteResponse.setDistrict_name_en(district.getNameEn());
                siteResponse.setDistrict_name_th(district.getNameTh());
                siteResponse.setDistrict_zipcode(district.getZipcode());
            }
        } catch(Exception e) {
            log.info("got District null from "+site.getCode());
        }

        try {
            if (site.getSub_district() != null && site.getSub_district().getId() != null) {
                SubDistrict subDistrict = site.getSub_district();
                siteResponse.setSub_district_id(subDistrict.getId());
                siteResponse.setSub_district_name_en(subDistrict.getNameEn());
                siteResponse.setSub_district_name_th(subDistrict.getNameTh());
            }
        } catch(Exception e) {
            log.info("got Sub_district null from "+site.getCode());
        }


        siteResponse.setSite_key(site.getSite_key());
        siteResponse.setInstalled_date(site.getInstalledDate());
        siteResponse.setIsInstall(site.getIsInstall());
        siteResponse.setUpdatedAt(site.getUpdatedAt());
        siteResponse.setActive(site.getActive());
        siteResponse.setLat(site.getLat());
        siteResponse.setLon(site.getLon());
        siteResponse.setIp_address(site.getIp_address());
        siteResponse.setPort(site.getPort());
        siteResponse.setSubnet_mask(site.getSubnet_mask());
        siteResponse.setDefault_gateway(site.getDefault_gateway());
        siteResponse.setAddress(site.getAddress());
        siteResponse.setImage1(site.getImage1());

        List<CabinetResponse> siteCabinets = site.getCabinets().stream()
                .sorted(Comparator.comparing(Cabinet::getCabinetNo))
                .map(cabinet -> {
            CabinetResponse cabinetRespose = new CabinetResponse();
            cabinetRespose.setId(cabinet.getId());
            cabinetRespose.setCabinet_code(cabinet.getCode());
            cabinetRespose.setCabinet_no(cabinet.getCabinetNo());
            cabinetRespose.setOnline(cabinet.getOnline());
            cabinetRespose.setDoorEventCode(cabinet.getDoorEventCode().name);
            cabinetRespose.setLockEventCode(cabinet.getLockEventCode().name);
            cabinetRespose.setActive(cabinet.getActive());
            cabinetRespose.setAuxin1(cabinet.getAuxin1());
            cabinetRespose.setAuxin2(cabinet.getAuxin2());
            cabinetRespose.setAuxin3(cabinet.getAuxin3());
            cabinetRespose.setAuxout(cabinet.getAuxout());
                    try {
                        cabinetRespose.setDevices(genDeviceResponse(cabinet));
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                    cabinetRespose.setAux_devices(genAuxDeviceResponse(cabinet));

            return cabinetRespose;
        }).collect(Collectors.toList());

        siteResponse.setCabinet_count(siteCabinets.size());
        siteResponse.setCabinets(siteCabinets);

        // Note: SiteVersion lookup removed to prevent N+1 queries
        // If site version is needed, consider adding a @OneToOne relationship to Site entity
        // or batch loading all versions upfront
        siteResponse.setVersion(null);

        siteResponse.setTemperature(site.getTemperature());
        siteResponse.setVersion_number(site.getVersion());
        siteResponse.setMotion(0);
        siteResponse.setFirmware_option(3);
        if(site.getMotion() != null){
            siteResponse.setMotion(site.getMotion());
        }
        if(site.getFirmware_option() != null){
            siteResponse.setFirmware_option(site.getFirmware_option());
        }

        return siteResponse;
    }

    public static SiteResponse mapSiteToSiteResponseOptimized(Site site) {
        // Simplified mapper - ไม่โหลด cabinets เพื่อป้องกัน N+1 queries
        SiteResponse siteResponse = new SiteResponse();
        siteResponse.setId(site.getId());
        siteResponse.setCode(site.getCode());
        siteResponse.setName_th(site.getName_th());
        siteResponse.setName_en(site.getName_en());
        
        siteResponse.setStatus(getStatusResponse(site.getStatus()));
        
        // Region - ควร eager loaded มาแล้ว
        if (site.getRegion() != null) {
            siteResponse.setRegion_id(site.getRegion().getId());
            siteResponse.setRegion_code(site.getRegion().getCode());
            siteResponse.setRegion_name(site.getRegion().getName());
        }
        
        // Province - ควร eager loaded มาแล้ว  
        if (site.getProvince() != null) {
            siteResponse.setProvince_id(site.getProvince().getId());
            siteResponse.setProvince_name_en(site.getProvince().getNameEn());
            siteResponse.setProvince_name_th(site.getProvince().getNameTh());
            siteResponse.setProvince_code(site.getProvince().getCode());
        }
        
        // District - ควร eager loaded มาแล้ว
        if (site.getDistrict() != null) {
            siteResponse.setDistrict_id(site.getDistrict().getId());
            siteResponse.setDistrict_name_en(site.getDistrict().getNameEn());
            siteResponse.setDistrict_name_th(site.getDistrict().getNameTh());
            siteResponse.setDistrict_zipcode(site.getDistrict().getZipcode());
        }
        
        // Sub District - ควร eager loaded มาแล้ว
        if (site.getSub_district() != null) {
            siteResponse.setSub_district_id(site.getSub_district().getId());
            siteResponse.setSub_district_name_en(site.getSub_district().getNameEn());
            siteResponse.setSub_district_name_th(site.getSub_district().getNameTh());
        }
        
        // Basic fields
        siteResponse.setSite_key(site.getSite_key());
        siteResponse.setInstalled_date(site.getInstalledDate());
        siteResponse.setIsInstall(site.getIsInstall());
        siteResponse.setUpdatedAt(site.getUpdatedAt());
        siteResponse.setActive(site.getActive());
        siteResponse.setLat(site.getLat());
        siteResponse.setLon(site.getLon());
        siteResponse.setIp_address(site.getIp_address());
        siteResponse.setPort(site.getPort());
        siteResponse.setSubnet_mask(site.getSubnet_mask());
        siteResponse.setDefault_gateway(site.getDefault_gateway());
        siteResponse.setAddress(site.getAddress());
        siteResponse.setImage1(site.getImage1());
        siteResponse.setTemperature(site.getTemperature());
        siteResponse.setVersion_number(site.getVersion());
        
        // Skip cabinets to prevent lazy loading
        siteResponse.setCabinet_count(0);
        siteResponse.setCabinets(Collections.emptyList());
        
        // Skip version lookup
        siteResponse.setVersion(null);
        
        // Motion and firmware
        siteResponse.setMotion(site.getMotion() != null ? site.getMotion() : 0);
        siteResponse.setFirmware_option(site.getFirmware_option() != null ? site.getFirmware_option() : 3);
        
        return siteResponse;
    }

    public static SiteResponseForWs mapSiteToSiteResponseForWs(Site site) {
        SiteResponseForWs siteResponse = new SiteResponseForWs();
        siteResponse.setId(site.getId());
        siteResponse.setCode(site.getCode());
        siteResponse.setName_th(site.getName_th());
        siteResponse.setName_en(site.getName_en());

        siteResponse.setStatus(getStatusResponse(site.getStatus()));

        siteResponse.setSite_key(site.getSite_key());
        siteResponse.setInstalled_date(site.getInstalledDate());
        siteResponse.setIsInstall(site.getIsInstall());
        siteResponse.setUpdatedAt(site.getUpdatedAt());
        siteResponse.setActive(site.getActive());
        siteResponse.setLat(site.getLat());
        siteResponse.setLon(site.getLon());
        siteResponse.setIp_address(site.getIp_address());
        siteResponse.setPort(site.getPort());
        siteResponse.setSubnet_mask(site.getSubnet_mask());
        siteResponse.setDefault_gateway(site.getDefault_gateway());

        List<CabinetResponse> siteCabinets = site.getCabinets().stream()
                .sorted(Comparator.comparing(Cabinet::getId))
                .map(cabinet -> {
                    CabinetResponse cabinetRespose = new CabinetResponse();
                    cabinetRespose.setId(cabinet.getId());
                    cabinetRespose.setCabinet_code(cabinet.getCode());
                    cabinetRespose.setCabinet_no(cabinet.getCabinetNo());
                    cabinetRespose.setOnline(cabinet.getOnline());
                    cabinetRespose.setDoorEventCode(cabinet.getDoorEventCode().name);
                    cabinetRespose.setLockEventCode(cabinet.getLockEventCode().name);
                    cabinetRespose.setActive(cabinet.getActive());
                    try {
                        cabinetRespose.setDevices(genDeviceResponse(cabinet));
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }

                    return cabinetRespose;
                }).collect(Collectors.toList());

        siteResponse.setCabinet_count(siteCabinets.size());
        siteResponse.setCabinets(siteCabinets);

        return siteResponse;
    }
}
