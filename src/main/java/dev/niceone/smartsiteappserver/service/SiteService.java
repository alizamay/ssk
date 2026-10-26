package dev.niceone.smartsiteappserver.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.async.Callback;
import com.mashape.unirest.http.exceptions.UnirestException;
import dev.niceone.smartsiteappserver.exception.AppException;
import dev.niceone.smartsiteappserver.exception.ResourceNotFoundException;
import dev.niceone.smartsiteappserver.mapper.SiteModelMapper;
import dev.niceone.smartsiteappserver.model.*;
import dev.niceone.smartsiteappserver.model.constant.DeviceTypeCodeEnum;
import dev.niceone.smartsiteappserver.model.constant.DoorEventCodeEnum;
import dev.niceone.smartsiteappserver.model.constant.LockEventCodeEnum;
import dev.niceone.smartsiteappserver.model.constant.StatusCodeEnum;
import dev.niceone.smartsiteappserver.payload.*;
import dev.niceone.smartsiteappserver.payload.request.CabinetLockRequest;
import dev.niceone.smartsiteappserver.payload.response.*;
import dev.niceone.smartsiteappserver.repository.*;
import dev.niceone.smartsiteappserver.security.UserPrincipal;
import dev.niceone.smartsiteappserver.specification.SearchOperation;
import dev.niceone.smartsiteappserver.specification.SiteSpecification;
import dev.niceone.smartsiteappserver.specification.SpecSearchCriteria;
import dev.niceone.smartsiteappserver.utils.AppConstants;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.Deflater;

@Service
@Slf4j
@Transactional(readOnly = true)
public class SiteService {
    private AlarmLogRepository alarmLogRepository;
    private CabinetRepository cabinetRepository;
    private DeviceRepository deviceRepository;
    private AuxDeviceRepository auxDeviceRepository;
    private DistrictRepository districtRepository;
    private ProvinceRepository provinceRepository;
    private RegionRepository regionRepository;
    private SiteRepository siteRepository;
    private SubDistrictRepository subDistrictRepository;
    private UserRepository userRepository;
    private SiteVersionRepository siteVersionRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    public SiteService(AlarmLogRepository alarmLogRepository,
                       CabinetRepository cabinetRepository,
                       DeviceRepository deviceRepository,
                       AuxDeviceRepository auxDeviceRepository,
                       DistrictRepository districtRepository,
                       ProvinceRepository provinceRepository,
                       RegionRepository regionRepository,
                       SiteRepository siteRepository,
                       SubDistrictRepository subDistrictRepository,
                       UserRepository userRepository,
                       SiteVersionRepository siteVersionRepository) {
        this.alarmLogRepository = alarmLogRepository;
        this.cabinetRepository = cabinetRepository;
        this.deviceRepository = deviceRepository;
        this.auxDeviceRepository = auxDeviceRepository;
        this.districtRepository = districtRepository;
        this.provinceRepository = provinceRepository;
        this.regionRepository = regionRepository;
        this.siteRepository = siteRepository;
        this.subDistrictRepository = subDistrictRepository;
        this.userRepository = userRepository;
        this.siteVersionRepository = siteVersionRepository;
    }

    @Autowired
    private Environment env;

    public static byte[] compressBytes(byte[] data) {
        Deflater deflater = new Deflater();
        deflater.setInput(data);
        deflater.finish();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);
        byte[] buffer = new byte[1024];
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            outputStream.write(buffer, 0, count);
        }

        try {
            outputStream.close();
        } catch (IOException e) {
        }

        System.out.println("Compressed Image Byte Size - " + outputStream.toByteArray().length);
        return outputStream.toByteArray();
    }

    private Cabinet generateCabinetFromRequest(CabinetRequest cabinetRequest, Site site) {
        Cabinet tmpCabinet = new Cabinet(
                cabinetRequest.getCabinet_code(),
                cabinetRequest.getCabinet_no(),
                cabinetRequest.getActive(),
                LockEventCodeEnum.getByName(cabinetRequest.getLockEventCode()),
                DoorEventCodeEnum.getByName(cabinetRequest.getDoorEventCode()),"");
        tmpCabinet.setSite(site);

        cabinetRequest.getDevices().forEach(deviceRequest -> {
            DeviceTypeCodeEnum deviceType = DeviceTypeCodeEnum.getByCode(deviceRequest.getCode());
            Device tmpDevice = new Device(deviceType, deviceRequest.isInstalled(),
                    deviceRequest.isEnabled(),
                    StatusCodeEnum.STATUS_UNKNOWN);
            System.out.println("tmpDevice: "+tmpDevice);
            System.out.println("tmpCabinet: "+ tmpCabinet);
            tmpCabinet.addDevice(tmpDevice);

            List<AuxDevice> auxes = tmpCabinet.getAux_devices();
            log.info("auxes "+auxes);
            if (auxes == null || auxes.isEmpty()) {
                if (tmpCabinet.getCabinetNo() == 1) {
                    tmpCabinet.addAuxDevice(new AuxDevice(0, false));
                    tmpCabinet.addAuxDevice(new AuxDevice(1, true));
                    tmpCabinet.addAuxDevice(new AuxDevice(2, true));
                    tmpCabinet.addAuxDevice(new AuxDevice(3, true));
                } else {
                    tmpCabinet.addAuxDevice(new AuxDevice(0, false));
                }
            }

            cabinetRequest.getAux_devices().forEach(auxDeviceRequest -> {
                List<AuxDevice> tmpAuxes = tmpCabinet.getAux_devices();
                tmpAuxes.forEach(auxDevice -> {
                    if (auxDeviceRequest.getAux_no() == auxDevice.getAuxNo()) {
                        auxDevice.setName(auxDeviceRequest.getAux_name());
                        auxDevice.setIs_active(auxDeviceRequest.getIs_active());
                    }
                });
            });
        });
        cabinetRepository.save(tmpCabinet);
        site.addCabinet(tmpCabinet);
        siteRepository.save(site);

        return tmpCabinet;
    }

    private ResponseEntity<ApiResponse> saveSiteObject(SiteRequest siteRequest, MultipartFile file1, Site site, String action) {
        if (action == "DELETE") {
            site.setActive(false);
        }
        else {
            Region region = regionRepository.findById(siteRequest.getRegion_id()).orElse(null);
            if (region == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponse(false, "Not found region id"));
            }

            Province province = provinceRepository.findById(siteRequest.getProvince_id()).orElse(null);
            if (province == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponse(false, "Not found province id"));
            }

            District district = districtRepository.findById(siteRequest.getDistrict_id()).orElse(null);
            if (district == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponse(false, "Not found district_id"));
            }

            SubDistrict subDistrict = subDistrictRepository.findById(siteRequest.getSub_district_id()).orElse(null);
            if (subDistrict == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponse(false, "Not found sub_district_id"));
            }

            site.setActive(true);
            site.setCode(siteRequest.getCode());
            site.setName_th(siteRequest.getName_th());
            site.setName_en(siteRequest.getName_en());
            site.setRegion(regionRepository.findById(siteRequest.getRegion_id()).orElse(null));
            site.setProvince(provinceRepository.findById(siteRequest.getProvince_id()).orElse(null));
            site.setDistrict(districtRepository.findById(siteRequest.getDistrict_id()).orElse(null));
            site.setSub_district(subDistrictRepository.findById(siteRequest.getSub_district_id()).orElse(null));
            site.setAddress(siteRequest.getAddress());
            site.setLat(siteRequest.getLat());
            site.setLon(siteRequest.getLon());
            site.setIp_address(siteRequest.getIp_address());
            site.setPort(siteRequest.getPort());
            site.setSubnet_mask(siteRequest.getSubnet_mask());
            site.setDefault_gateway(siteRequest.getDefault_gateway());
            site.setInstalledDate(siteRequest.getInstalled_date());
            site.setIsInstall(siteRequest.getIsInstall());
            site.setImage1(file1);

//            DeviceType siteType = null;
//            List<DeviceType> siteTypes = deviceTypeRepository.findByNameEnAndActive("Site", true);
//            if (siteTypes.isEmpty()) {
//                siteType = new DeviceType("Site", "Site");
//                deviceTypeRepository.save(siteType);
//            } else {
//                siteType = siteTypes.get(0);
//            }
        }

        if (action == "UPDATE") {
            siteRequest.getCabinets().forEach(cabinetRequest -> {
                Cabinet tmpCabinet = null;
                if (cabinetRequest.getId() != null) {
                    tmpCabinet = cabinetRepository.findById(cabinetRequest.getId()).orElse(null);
                } else {
                    tmpCabinet = cabinetRepository.findBySiteAndCabinetNo(site, cabinetRequest.getCabinet_no());
                }

                if (tmpCabinet == null) {
                    tmpCabinet = generateCabinetFromRequest(cabinetRequest, site);
                }

                tmpCabinet.setCode(cabinetRequest.getCabinet_code());
                log.info(cabinetRequest.getCabinet_no()+" active: "+cabinetRequest.getActive());

                tmpCabinet.setActive(cabinetRequest.getActive());

                Cabinet finalTmpCabinet = tmpCabinet;
                cabinetRequest.getDevices().forEach(deviceRequest -> {
                    Device tmpDevice = null;
                    if (deviceRequest.getId() != null ){
                        tmpDevice = deviceRepository.findById(deviceRequest.getId()).orElse(null);
                    } else {
                        DeviceTypeCodeEnum deviceType = DeviceTypeCodeEnum.getByCode(deviceRequest.getCode());
                        tmpDevice = deviceRepository.findFirstByCabinetAndDeviceTypeCode(finalTmpCabinet, deviceType);
                    }

                    if (tmpDevice == null) {
                        DeviceTypeCodeEnum deviceType = DeviceTypeCodeEnum.getByCode(deviceRequest.getCode());
                        tmpDevice = new Device(deviceType, deviceRequest.isInstalled(),
                                deviceRequest.isEnabled(),
                                StatusCodeEnum.STATUS_UNKNOWN);
                        finalTmpCabinet.addDevice(tmpDevice);
                    }

                    tmpDevice.setIs_enable(deviceRequest.isEnabled());
                    tmpDevice.setIs_install(deviceRequest.isInstalled());
                    tmpDevice = deviceRepository.save(tmpDevice);

                    finalTmpCabinet.addDevice(tmpDevice);
                });

                List<AuxDevice> auxes = finalTmpCabinet.getAux_devices();
                log.info("auxes "+auxes);
                if (auxes == null || auxes.isEmpty()) {
                    if (finalTmpCabinet.getCabinetNo() == 1) {
                        finalTmpCabinet.addAuxDevice(new AuxDevice(0, false));
                        finalTmpCabinet.addAuxDevice(new AuxDevice(1, true));
                        finalTmpCabinet.addAuxDevice(new AuxDevice(2, true));
                        finalTmpCabinet.addAuxDevice(new AuxDevice(3, true));
                    } else {
                        finalTmpCabinet.addAuxDevice(new AuxDevice(0, false));
                    }
                    cabinetRepository.save(finalTmpCabinet);
                }

                cabinetRequest.getAux_devices().forEach(auxDeviceRequest -> {
                    AuxDevice tmpDevice = null;
                    if (auxDeviceRequest.getId() != null ){
                        tmpDevice = auxDeviceRepository.findById(auxDeviceRequest.getId()).orElse(null);
                        tmpDevice.setIs_active(auxDeviceRequest.getIs_active());
                        tmpDevice.setAuxNo(auxDeviceRequest.getAux_no());
                        tmpDevice.setName(auxDeviceRequest.getAux_name());
                        tmpDevice.setIs_input(auxDeviceRequest.getIs_input());
                        tmpDevice = auxDeviceRepository.save(tmpDevice);
                    } else {
                        List<AuxDevice> tmpAuxes = finalTmpCabinet.getAux_devices();
                        tmpAuxes.forEach(auxDevice -> {
                            if (auxDeviceRequest.getAux_no() == auxDevice.getAuxNo()) {
                                auxDevice.setName(auxDeviceRequest.getAux_name());
                                auxDevice.setIs_active(auxDeviceRequest.getIs_active());
                            }
                        });
                    }

//                    finalTmpCabinet.addAuxDevice(tmpDevice);
                });
                cabinetRepository.save(tmpCabinet);
            });
        } else if (action == "CREATE") {
            site.setStatus(StatusCodeEnum.STATUS_UNKNOWN);
            siteRequest.getCabinets().forEach(cabinetRequest -> {
                Cabinet tmpCabinet = new Cabinet(
                        cabinetRequest.getCabinet_code(),
                        cabinetRequest.getCabinet_no(),
                        cabinetRequest.getActive(),
                        LockEventCodeEnum.getByName(cabinetRequest.getLockEventCode()),
                        DoorEventCodeEnum.getByName(cabinetRequest.getDoorEventCode()),"");

                cabinetRequest.getDevices().forEach(deviceRequest -> {
                    DeviceTypeCodeEnum deviceType = DeviceTypeCodeEnum.getByCode(deviceRequest.getCode());
                    Device tmpDevice = new Device(deviceType, deviceRequest.isInstalled(),
                            deviceRequest.isEnabled(),
                            StatusCodeEnum.STATUS_UNKNOWN);
                    System.out.println("tmpDevice: "+tmpDevice);
                    System.out.println("tmpCabinet: "+ tmpCabinet);

                    tmpCabinet.addDevice(tmpDevice);

                    List<AuxDevice> auxes = tmpCabinet.getAux_devices();
                    log.info("auxes "+auxes);
                    if (auxes == null || auxes.isEmpty()) {
                        if (tmpCabinet.getCabinetNo() == 1) {
                            tmpCabinet.addAuxDevice(new AuxDevice(0, false));
                            tmpCabinet.addAuxDevice(new AuxDevice(1, true));
                            tmpCabinet.addAuxDevice(new AuxDevice(2, true));
                            tmpCabinet.addAuxDevice(new AuxDevice(3, true));
                        } else {
                            tmpCabinet.addAuxDevice(new AuxDevice(0, false));
                        }
                    }

                    cabinetRequest.getAux_devices().forEach(auxDeviceRequest -> {
                        List<AuxDevice> tmpAuxes = tmpCabinet.getAux_devices();
                        tmpAuxes.forEach(auxDevice -> {
                            if (auxDeviceRequest.getAux_no() == auxDevice.getAuxNo()) {
                                auxDevice.setName(auxDeviceRequest.getAux_name());
                                auxDevice.setIs_active(auxDeviceRequest.getIs_active());
                            }
                        });
                    });
                });
                site.addCabinet(tmpCabinet);
            });
        }
        siteRepository.save(site);
        SiteResponseForWs response = SiteModelMapper.mapSiteToSiteResponseForWs(site);
        messagingTemplate.convertAndSend("/topic/site_report", response);

        if (action=="CREATE") {
            URI location = ServletUriComponentsBuilder
                    .fromCurrentRequest().path("/{id}")
                    .buildAndExpand(site.getId()).toUri();
            try {
                updateToTTSM(site);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return ResponseEntity.created(location)
                    .body(new ApiResponse(true, "Site Created Successfully"));
        } else {
            try {
                updateToTTSM(site);

                ObjectMapper mapper = new ObjectMapper();
                String siteRPTJSONString = mapper.writeValueAsString(siteRequest);
                SendConfigToSite(site.getIp_address(),site.getPort(),siteRPTJSONString);

            } catch (Exception e) {
                e.printStackTrace();
            }
            return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse(true, "UPDATE OK"));
        }
    }

    @Transactional
    public ResponseEntity<ApiResponse>  createSite(SiteRequest siteRequest, MultipartFile file1) {
        Site oldSite = siteRepository.findByCode(siteRequest.getCode()).orElse(null);
        if (oldSite != null) {
            return saveSiteObject(siteRequest, file1, oldSite, "UPDATE");
        } else {
            Site site = new Site();
            return saveSiteObject(siteRequest, file1, site, "CREATE");
        }
    }

    @Transactional
    public ResponseEntity<ApiResponse> updateSite(Long id, SiteRequest siteRequest, MultipartFile file1) {
        Site site = siteRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Site", "id", id));

        return saveSiteObject(siteRequest, file1, site, "UPDATE");
//
//        Region region = regionRepository.findById(siteRequest.getRegion_id()).orElse(null);
//        if (region == null) {
//            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
//                    .body(new ApiResponse(false, "Not found region_id"));
//        }
//        siteRepository.findById(id).map(site -> {
//            log.error("siteRepository found site: "+site.getCode());
//            site.setActive(true);
//            site.setCode(siteRequest.getCode());
//            site.setName_th(siteRequest.getName_th());
//            site.setRegion(regionRepository.findById(siteRequest.getRegion_id()).orElse(null));
//
//            System.out.println(""+siteRequest);
//            return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse(true, "UPDATE OK"));
//            //        siteRepository.save(site);
//            //        URI location = ServletUriComponentsBuilder
//            //                .fromCurrentRequest().path("/{id}")
//            //                .buildAndExpand(site.getId()).toUri();
//
//            //        return ResponseEntity.created(location)
//            //                .body(new ApiResponse(true, "Site Created Successfully"));
//        }).orElseGet(() -> {
//            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
//                    .body(new ApiResponse(false, "Not found site id"));
//        });

//        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
//                .body(new ApiResponse(false, "Unknow err."));
    }

    @Transactional
    public ResponseEntity<ApiResponse> deleteSite(Long id, SiteRequest siteRequest) {
        Site site = siteRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Site", "id", id));
        return saveSiteObject(siteRequest, null, site, "DELETE");
    }

    public SiteResponse getSiteById(Long siteId) {
        Site site = siteRepository.findById(siteId).orElseThrow(
                () -> new ResourceNotFoundException("Site", "id", siteId));

        return SiteModelMapper.mapSiteToSiteResponse(site,
                provinceRepository, districtRepository, subDistrictRepository, siteVersionRepository);
    }

    public Optional<Site> getSiteByCode(String code) {
        return siteRepository.findByCode(code);
    }

    @Transactional(readOnly = true)
    public PagedResponse<SiteResponse> getAll(Specification<Site> spec, int page, int size, UserPrincipal currentUser) {
        return getAll(page, size, currentUser, null, null, null, null);
    }

    @Transactional(readOnly = true)
    public PagedResponse<SiteResponse> getAll(int page, int size, UserPrincipal currentUser,
                                              String status, Long regionId, String keyword, Boolean active) {
        long startTime = System.currentTimeMillis();
        try {
            // Set default status to STATUS_UP only if not provided (null)
            // Empty string means "all statuses" (no filter)
            if (status == null) {
                status = "STATUS_UP";
            } else if (status.isEmpty()) {
                status = null;
            }

            log.debug("Starting getAll() for user: {} page: {} size: {} filters: [status={}, regionId={}, keyword={}, active={}]",
                    currentUser.getUsername(), page, size, status, regionId, keyword, active);

            // 1. หา user
            User user = userRepository.findById(currentUser.getId())
                    .orElseThrow(() -> new AppException("User not found"));

            // 2. ใช้ JDBC Template สำหรับ performance ที่ดีที่สุด
            log.debug("Using JDBC Template optimized query with filters for user: {}", user.getUsername());

            List<SiteResponse> siteResponses = siteRepository.findSitesForUserOptimized(
                    currentUser.getId(), page, size, status, regionId, keyword, active);
            long totalElements = siteRepository.countSitesForUser(
                    currentUser.getId(), status, regionId, keyword, active);

            // 3. Load cabinets, devices, and aux_devices for all sites
            log.debug("Loading cabinets for {} sites", siteResponses.size());
            siteRepository.loadCabinetsForSites(siteResponses);

            int totalPages = (int) Math.ceil((double) totalElements / size);
            boolean isLast = (page + 1) >= totalPages;

            long totalTime = System.currentTimeMillis() - startTime;
            log.debug("JDBC getAll completed in {}ms - page: {}/{}, sites: {}/{}",
                    totalTime, page + 1, totalPages,
                    siteResponses.size(), totalElements);

            return new PagedResponse<>(
                    siteResponses,
                    page,
                    size,
                    totalElements,
                    totalPages,
                    isLast
            );

        } catch (Exception e) {
            long totalTime = System.currentTimeMillis() - startTime;
            log.error("Error in getAll() after {}ms: {}", totalTime, e.getMessage(), e);
            throw new AppException("Failed to fetch sites: " + e.getMessage());
        }
    }

    private void updateToTTSM(Site site) throws Exception    {
        // Update site and all of its existing cabinets to TTSM
        // Make column names according to TTSM
        /*
        [
            {
                "cabinetList": [
                    {
                        "isCabinetActive": true,
                        "cabinetCode": "BKK0001-1"
                    },
                    {
                        "isCabinetActive": true,
                        "cabinetCode": "BKK0001-3"
                    },
                    {
                        "isCabinetActive": true,
                        "cabinetCode": "BKK0001-2"
                    }
                ],
                "siteCode": "BKK0001",
                "isSiteActive": true
            }
        ]
        */

        JSONArray outputArray = new JSONArray();
        JSONObject siteJson = new JSONObject();

        String siteCode = site.getCode();
        boolean isSiteActive = (boolean) site.getActive();
        String isSiteActive_str;
        if(isSiteActive) isSiteActive_str = "True"; else isSiteActive_str = "False";

        siteJson
                .put("siteCode", siteCode)
                .put("isSiteActive", isSiteActive_str)
        ;

        // Creating cabinet array
        List<Cabinet> cabinets = site.getCabinets();
        JSONArray cabinetList = new JSONArray();

        for (Cabinet cabinet : cabinets) {
            String cabinetCode = cabinet.getCode();
            boolean isCabinetActive = (boolean) cabinet.getActive();
            //boolean isCabinetInstalled = (boolean) siteMasterList.get(k).get("is_installed");

            String isCabinetActive_str = "";
            if(isCabinetActive) isCabinetActive_str = "True"; else isCabinetActive_str = "False";

            //if(isCabinetInstalled) {
            cabinetList.put(new JSONObject()
                    .put("cabinetCode", cabinetCode)
                    .put("isCabinetActive", isCabinetActive_str)
            );
        }

        siteJson.put("cabinetList", cabinetList);
        outputArray.put(siteJson);
        log.info("prepare update master json...");
        log.info(""+outputArray);

        // Send to TTSM using the same technique as Lock/Unlocking a cabinet
        submitToTTSM(outputArray.toString());

//        ActivityLog.saveLog(
//                "updateToTTSM",
//                "TTSM Master Data Update",
//                outputArray.toString(),
//                "Update Sent");
    }

    private void submitToTTSM(String jsonString) throws JSONException, IOException {
        String apiKeyByTTSM = env.getProperty("apikey_by_ttsm");
        String ttsmIP = env.getProperty("ttsm_ip");
        String ttsmPort = env.getProperty("ttsm_port");
        String ttsmEndpoint_updateMaster = env.getProperty("ttsm_endpoint_update_master");
        String targetUrl = "" + ttsmIP + ":" + ttsmPort + "/" + ttsmEndpoint_updateMaster;

        log.info("    "+targetUrl);
        Unirest.post(targetUrl)
            .header("Content-Type", "application/json")
            .header("apiKey", apiKeyByTTSM)
            .body(jsonString)
            .asJsonAsync(new Callback<JsonNode>() {
                @Override
                public void completed(HttpResponse<JsonNode> response) {
//                            ActivityLog.saveLog(
//                                        "submitToTTSM",
//                                        "TTSM Master Data Update Result",
//                                        "statusCode: " + response.getStatus() + " body: " + response.getBody().toString(),
//                                        "Update Success");
                    log.info("TTSM Master Data Update completed Result: " + response.getStatus());
                    log.info("    "+response.getBody());
                }

                @Override
                public void failed(UnirestException e) {
                    //log request error
//                            ActivityLog.saveLog(
//                                    "submitToTTSM",
//                                    "TTSM Master Data Update Result",
//                                    "statusCode: " + response.getStatus() + " body: " + response.getBody().toString(),
//                                    "Update Failed");
                    log.info("TTSM Master Data Update failed UnirestException: " + e.getMessage());
                }

                @Override
                public void cancelled() {
                    //log request cancelled
//                            ActivityLog.saveLog(
//                                    "submitToTTSM",
//                                    "TTSM Master Data Update Result",
//                                    "statusCode: " + response.getStatus() + " body: " + response.getBody().toString(),
//                                    "Update Cancelled");
                    log.info("TTSM Master Data Update cancelled: " );
                }
            });

    }

    public ResponseEntity<DeviceHealthCheckResp> healthcheck(Long siteId) {
        Site site = siteRepository.findById(siteId).orElseThrow(
                () -> new ResourceNotFoundException("Site", "id", siteId));

        log.info("healthcheck " + site.getCode());
        ObjectMapper objectMapper = new ObjectMapper();
        if (site == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new DeviceHealthCheckResp());
        }
        String ip = site.getIp_address();
        int port = site.getPort();


//        ip = "127.0.0.1";
//        port = 8081;

        String targetUrl = "http://"+ip+":"+port+ AppConstants.HEALTHCHECK_URL;

        log.info("call healthcheck API on Real Device "+targetUrl+" "+site.getCode());

        HashMap<String, String> result = new HashMap<>();
        String errMsg = "";
        boolean isError = false;
        String apikey_for_device = env.getProperty("apikey_for_device");
        try {
            HttpResponse<String> response = Unirest.post(targetUrl)
                    .header("Content-Type", "application/json")
                    .header("apiKey", apikey_for_device)
                    .body("{\"site_code\": \"" + site.getCode() + "\", \"cabinet_code\":\""+site.getCode()+"-1"+"\"}")
                    .asString();

            String responseString = response.getBody();
            log.info("responseString: "+responseString);
            if (response.getStatus() == 200) {

                DeviceHealthCheckResp hc = new DeviceHealthCheckResp();
                try {
                    hc = objectMapper.readValue(responseString, DeviceHealthCheckResp.class);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }

                return ResponseEntity.status(HttpStatus.OK).body(hc);
            } else {
                DeviceHealthCheckResp hc = new DeviceHealthCheckResp();

                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(hc);
            }

            // Retrieve the parsed JSONObject from the response
//            String responseString = response.getBody();
//            JSONObject jsonResp = new JSONObject(responseString);
//            log.info("responseString: "+responseString);
//            boolean is_success = (boolean) jsonResp.get("is_success");
//            if (is_success == true) {
//                isError = false;
//            } else {
//                isError = true;
//            }
//            errMsg = String.valueOf( jsonResp.get("err_msg") );

        } catch (UnirestException e) {
            e.printStackTrace();
            isError = true;
            errMsg = "Failed to communicate with targeted site.\n" + ip+":"+port;
            log.info("errMsg "+errMsg);
            DeviceHealthCheckResp hc = new DeviceHealthCheckResp();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(hc);
        } catch (org.json.JSONException e) {
            e.printStackTrace();
            isError = true;
            errMsg = "Aux out parse json err site.\n" + ip+":"+port;
            log.info("errMsg "+errMsg);
            DeviceHealthCheckResp hc = new DeviceHealthCheckResp();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(hc);
        }
    }

    public ResponseEntity<ApiResponse> onWifi(Long siteId) {
        Site site = siteRepository.findById(siteId).orElseThrow(
                () -> new ResourceNotFoundException("Site", "id", siteId));

        log.info("onWifi " + site.getCode());

        if (site == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(true, "No Site"));
        }
        String ip = site.getIp_address();
        int port = site.getPort();

        String targetUrl = "http://"+ip+":"+port+ AppConstants.ON_WIFI_URL;

        log.info("call onWifi API on Real Device "+targetUrl+" "+site.getCode());

        HashMap<String, String> result = new HashMap<>();
        String errMsg = "";
        boolean isError = false;
        String apikey_for_device = env.getProperty("apikey_for_device");
        try {
            HttpResponse<String> response = Unirest.post(targetUrl)
                    .header("Content-Type", "application/json")
                    .header("apiKey", apikey_for_device)
                    .body("{\"site_code\": \"" + site.getCode() + "\", \"cabinet_code\":\""+site.getCode()+"-1"+"\"}")
                    .asString();

            String responseString = response.getBody();
            log.info("responseString: "+responseString);
            if (response.getStatus() == 200) {
                return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse(true, "OK"));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse(false, ""+response.getStatusText()));
            }
        } catch (UnirestException e) {
            e.printStackTrace();
            isError = true;
            errMsg = "Failed to communicate with targeted site.\n" + ip+":"+port;
            log.info("errMsg "+errMsg);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(false, errMsg));
        } catch (org.json.JSONException e) {
            e.printStackTrace();
            isError = true;
            errMsg = "onWifi parse json err site.\n" + ip+":"+port;
            log.info("errMsg "+errMsg);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse(false, errMsg));
        }
    }

    public ResponseEntity<ApiResponse> offWifi(Long siteId) {
        Site site = siteRepository.findById(siteId).orElseThrow(
                () -> new ResourceNotFoundException("Site", "id", siteId));

        log.info("offWifi " + site.getCode());

        if (site == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(false, "No Site"));
        }
        String ip = site.getIp_address();
        int port = site.getPort();

        String targetUrl = "http://"+ip+":"+port+ AppConstants.OFF_WIFI_URL;

        log.info("call offWifi API on Real Device "+targetUrl+" "+site.getCode());

        HashMap<String, String> result = new HashMap<>();
        String errMsg = "";
        boolean isError = false;
        String apikey_for_device = env.getProperty("apikey_for_device");
        try {
            HttpResponse<String> response = Unirest.post(targetUrl)
                    .header("Content-Type", "application/json")
                    .header("apiKey", apikey_for_device)
                    .body("{\"site_code\": \"" + site.getCode() + "\", \"cabinet_code\":\""+site.getCode()+"-1"+"\"}")
                    .asString();

            String responseString = response.getBody();
            log.info("responseString: "+responseString);
            if (response.getStatus() == 200) {
                return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse(true, "OK"));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse(false, ""+response.getStatusText()));
            }
        } catch (UnirestException e) {
            e.printStackTrace();
            isError = true;
            errMsg = "Failed to communicate with targeted site.\n" + ip+":"+port;
            log.info("errMsg "+errMsg);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(false, errMsg));
        } catch (org.json.JSONException e) {
            e.printStackTrace();
            isError = true;
            errMsg = "offWifi parse json err site.\n" + ip+":"+port;
            log.info("errMsg "+errMsg);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse(false, errMsg));
        }
    }

    public ResponseEntity<WifiStatusResponse> checkWifi(Long siteId) {
        Site site = siteRepository.findById(siteId).orElseThrow(
                () -> new ResourceNotFoundException("Site", "id", siteId));

        log.info("checkWifi " + site.getCode());

        if (site == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new WifiStatusResponse(false));
        }
        String ip = site.getIp_address();
        int port = site.getPort();

        String targetUrl = "http://"+ip+":"+port+ AppConstants.CHECK_WIFI_URL;

        log.info("call checkWifi API on Real Device "+targetUrl+" "+site.getCode());

        HashMap<String, String> result = new HashMap<>();
        String errMsg = "";
        boolean isError = false;
        String apikey_for_device = env.getProperty("apikey_for_device");
        try {
            HttpResponse<String> response = Unirest.post(targetUrl)
                    .header("Content-Type", "application/json")
                    .header("apiKey", apikey_for_device)
                    .body("{\"site_code\": \"" + site.getCode() + "\", \"cabinet_code\":\""+site.getCode()+"-1"+"\"}")
                    .asString();

            // Retrieve the parsed JSONObject from the response
            String responseString = response.getBody();
            log.info("responseString: "+responseString);
            JSONObject jsonResp = new JSONObject(responseString);
            boolean is_on = false;
            try {
                is_on = (boolean) Boolean.parseBoolean(jsonResp.getString("wifi"));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            return ResponseEntity.status(HttpStatus.OK).body(new WifiStatusResponse(is_on));
        } catch (UnirestException e) {
            e.printStackTrace();
            isError = true;
            errMsg = "Failed to communicate with targeted site.\n" + ip+":"+port;
            log.info("errMsg "+errMsg);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new WifiStatusResponse(false));
        } catch (org.json.JSONException e) {
            e.printStackTrace();
            isError = true;
            errMsg = "checkWifi parse json err site.\n" + ip+":"+port;
            log.info("errMsg "+errMsg);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new WifiStatusResponse(false));
        }
    }

    public ResponseEntity<ApiResponse> startStream(Long siteId) {
        Site site = siteRepository.findById(siteId).orElseThrow(
                () -> new ResourceNotFoundException("Site", "id", siteId));

        log.info("startStream " + site.getCode());

        if (site == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(true, "No Site"));
        }
        String ip = site.getIp_address();
        int port = site.getPort();

        String targetUrl = "http://"+ip+":"+port+ AppConstants.RTMP_PUBLISH_URL;

        log.info("call startStream API on Real Device "+targetUrl+" "+site.getCode());

        HashMap<String, String> result = new HashMap<>();
        String errMsg = "";
        boolean isError = false;
        String apikey_for_device = env.getProperty("apikey_for_device");
        try {
            HttpResponse<String> response = Unirest.post(targetUrl)
                    .header("Content-Type", "application/json")
                    .header("apiKey", apikey_for_device)
                    .body("{\"site_code\": \"" + site.getCode() + "\", \"cabinet_code\":\""+site.getCode()+"-1"+"\"}")
                    .asString();

            String responseString = response.getBody();
            log.info("responseString: "+responseString);
            if (response.getStatus() == 200) {
                return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse(true, "OK"));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse(false, ""+response.getStatusText()));
            }
        } catch (UnirestException e) {
            e.printStackTrace();
            isError = true;
            errMsg = "Failed to communicate with targeted site.\n" + ip+":"+port;
            log.info("errMsg "+errMsg);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(false, errMsg));
        } catch (org.json.JSONException e) {
            e.printStackTrace();
            isError = true;
            errMsg = "startStream parse json err site.\n" + ip+":"+port;
            log.info("errMsg "+errMsg);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse(false, errMsg));
        }
    }

    public ResponseEntity<ApiResponse> stopStream(Long siteId) {
        Site site = siteRepository.findById(siteId).orElseThrow(
                () -> new ResourceNotFoundException("Site", "id", siteId));

        log.info("stopStream " + site.getCode());

        if (site == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(false, "No Site"));
        }
        String ip = site.getIp_address();
        int port = site.getPort();

        String targetUrl = "http://"+ip+":"+port+ AppConstants.RTMP_STOP_URL;
        log.info("call stopStream API on Real Device "+targetUrl+" "+site.getCode());

        HashMap<String, String> result = new HashMap<>();
        String errMsg = "";
        boolean isError = false;
        String apikey_for_device = env.getProperty("apikey_for_device");
        try {
            HttpResponse<String> response = Unirest.post(targetUrl)
                    .header("Content-Type", "application/json")
                    .header("apiKey", apikey_for_device)
                    .body("{\"site_code\": \"" + site.getCode() + "\", \"cabinet_code\":\""+site.getCode()+"-1"+"\"}")
                    .asString();

            String responseString = response.getBody();
            log.info("responseString: "+responseString);
            if (response.getStatus() == 200) {
                return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse(true, "OK"));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse(false, ""+response.getStatusText()));
            }
        } catch (UnirestException e) {
            e.printStackTrace();
            isError = true;
            errMsg = "Failed to communicate with targeted site.\n" + ip+":"+port;
            log.info("errMsg "+errMsg);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(false, errMsg));
        } catch (org.json.JSONException e) {
            e.printStackTrace();
            isError = true;
            errMsg = "stopStream parse json err site.\n" + ip+":"+port;
            log.info("errMsg "+errMsg);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse(false, errMsg));
        }
    }

    private Map<String, String> SendConfigToSite(String ip, int port, String jsonSite) {

        String targetUrl = "http://"+ip+":"+port+AppConstants.SITE_CONFIG;

        log.info("Send Config To Site "+targetUrl);

        HashMap<String, String> result = new HashMap<>();
        String errMsg = "";
        boolean isError = false;
        String apikey_for_device = env.getProperty("apikey_for_device");
        try {
            HttpResponse<String> response = Unirest.post(targetUrl)
                    .header("Content-Type", "application/json")
                    .header("apiKey", apikey_for_device)
                    .body(jsonSite)
                    .asString();

            // Retrieve the parsed JSONObject from the response
            String responseString = response.getBody();
            JSONObject jsonResp = new JSONObject(responseString);
            log.info("responseString: "+responseString);
            boolean is_success = (boolean) jsonResp.get("is_success");

            log.info("msg "+errMsg);
        } catch (UnirestException e) {
            e.printStackTrace();
            isError = true;
            errMsg = "Failed to communicate with targeted site.\n" + ip+":"+port;
            log.info("errMsg "+errMsg);
        } catch (org.json.JSONException e) {
            e.printStackTrace();
            isError = true;
            errMsg = "data site out parse json err site.\n" + ip+":"+port;
            log.info("errMsg "+errMsg);
        }

        result.put("isError", ""+isError);
        result.put("errMsg", errMsg);
        log.info("return result: "+result);
        return result;
    }


    public ResponseEntity<ApiResponse> setMotion(Long siteId,int motion) {
        Site site = siteRepository.findById(siteId).orElseThrow(
                () -> new ResourceNotFoundException("Site", "id", siteId));

        log.info("setMotion " + site.getCode());

        if (site == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(true, "No Site"));
        }
        String ip = site.getIp_address();
        int port = site.getPort();

        String targetUrl = "http://"+ip+":"+port+ AppConstants.MOTION_TIME;

        log.info("setMotion API on Real Device "+targetUrl+" "+site.getCode());

        HashMap<String, String> result = new HashMap<>();
        String errMsg = "";
        boolean isError = false;
        String apikey_for_device = env.getProperty("apikey_for_device");
        try {
            HttpResponse<String> response = Unirest.post(targetUrl)
                    .header("Content-Type", "application/json")
                    .header("apiKey", apikey_for_device)
                    .body("{\"cabinet_code\": \"" + site.getCode()+"-1" + "\", \"motion_time\":\""+ motion +"\"}")
                    .asString();

            String responseString = response.getBody();
            log.info("responseString: "+responseString);
            if (response.getStatus() == 200) {

                try{
                    site.setMotion(motion);
                    siteRepository.saveAndFlush(site);
                }catch (Exception se){
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse(false, "Save Motion Data Error"));
                }


                return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse(true, "OK"));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse(false, ""+response.getStatusText()));
            }
        } catch (UnirestException e) {
            e.printStackTrace();
            isError = true;
            errMsg = "Failed to communicate with targeted site.\n" + ip+":"+port;
            log.info("errMsg "+errMsg);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(false, errMsg));
        } catch (org.json.JSONException e) {
            e.printStackTrace();
            isError = true;
            errMsg = "onWifi parse json err site.\n" + ip+":"+port;
            log.info("errMsg "+errMsg);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse(false, errMsg));
        }
    }


    public ResponseEntity<ApiResponse> setVersion(Long siteId,String version) {
        Site site = siteRepository.findById(siteId).orElseThrow(
                () -> new ResourceNotFoundException("Site", "id", siteId));

        log.info("setVersion " + site.getCode());

        if (site == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(true, "No Site"));
        }

        try{

            SiteVersion siteVersion = siteVersionRepository.findFirstBySiteId(siteId).orElse(null);
            if(siteVersion != null){
                siteVersion.setVersionName(version);
                siteVersionRepository.save(siteVersion);
            }else{
                siteVersion = new SiteVersion();
                siteVersion.setSiteId(site.getId());
                siteVersion.setActive(true);
                siteVersion.setCreatedAt(LocalDateTime.now());
                siteVersion.setUpdatedAt(LocalDateTime.now());
                siteVersion.setVersionName(version);
                siteVersionRepository.save(siteVersion);
            }

            return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse(true, "OK"));
        }catch (Exception ex){
            ex.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse(false, ex.getMessage()));
        }

    }

    public ResponseEntity<ApiResponse> setFirmwareOption(Long siteId,int firmwareOption) {
        Site site = siteRepository.findById(siteId).orElseThrow(
                () -> new ResourceNotFoundException("Site", "id", siteId));

        log.info("setFirmwareOption " + site.getCode());

        if (site == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(true, "No Site"));
        }
        String ip = site.getIp_address();
        int port = site.getPort();

        String targetUrl = "http://"+ip+":"+port+ AppConstants.FIRMWARE_OPTION;

        log.info("FIRMWARE_OPTION API on Real Device "+targetUrl+" "+site.getCode());

        HashMap<String, String> result = new HashMap<>();
        String errMsg = "";
        boolean isError = false;
        String apikey_for_device = env.getProperty("apikey_for_device");
        try {
            HttpResponse<String> response = Unirest.post(targetUrl)
                    .header("Content-Type", "application/json")
                    .header("apiKey", apikey_for_device)
                    .body("{\"cabinet_code\": \"" + site.getCode()+"-1" + "\", \"firmwareOption\":\""+ firmwareOption +"\"}")
                    .asString();

            String responseString = response.getBody();
            log.info("responseString: "+responseString);
            if (response.getStatus() == 200) {

                try{
                    site.setFirmware_option(firmwareOption);
                    siteRepository.saveAndFlush(site);
                }catch (Exception se){
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse(false, "Save Motion Data Error"));
                }


                return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse(true, "OK"));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse(false, ""+response.getStatusText()));
            }
        } catch (UnirestException e) {
            e.printStackTrace();
            isError = true;
            errMsg = "Failed to communicate with targeted site.\n" + ip+":"+port;
            log.info("errMsg "+errMsg);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(false, errMsg));
        } catch (org.json.JSONException e) {
            e.printStackTrace();
            isError = true;
            errMsg = "onWifi parse json err site.\n" + ip+":"+port;
            log.info("errMsg "+errMsg);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse(false, errMsg));
        }
    }

    public ResponseEntity<ApiResponse> pingSite(Long siteId) {
        Site site = siteRepository.findById(siteId).orElseThrow(
                () -> new ResourceNotFoundException("Site", "id", siteId));

        log.info("pingSite " + site.getCode());

        if (site == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(true, "No Site"));
        }
        String ipAddress = site.getIp_address();

        try {
            InetAddress inet = InetAddress.getByName(ipAddress);
            boolean reachable = inet.isReachable(5000); // Timeout of 5000 milliseconds
            if(reachable){
                return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse(true, "OK"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(true, "Ping Not Found."));
    }

    public ResponseEntity<ApiResponse> pingSiteGateway(Long siteId) {
        Site site = siteRepository.findById(siteId).orElseThrow(
                () -> new ResourceNotFoundException("Site", "id", siteId));

        log.info("pingSiteGateway " + site.getCode());

        if (site == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(true, "No Site"));
        }
        String ipAddress = site.getDefault_gateway();

        try {
            InetAddress inet = InetAddress.getByName(ipAddress);
            boolean reachable = inet.isReachable(5000); // Timeout of 5000 milliseconds
            if(reachable){
                return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse(true, "OK"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(true, "Ping Not Found."));
    }

    //EOF
}
