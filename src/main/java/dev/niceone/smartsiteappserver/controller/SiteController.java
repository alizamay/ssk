package dev.niceone.smartsiteappserver.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.opencsv.bean.ColumnPositionMappingStrategy;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import dev.niceone.smartsiteappserver.exception.ResourceNotFoundException;
import dev.niceone.smartsiteappserver.model.*;
import dev.niceone.smartsiteappserver.model.constant.DeviceTypeCodeEnum;
import dev.niceone.smartsiteappserver.model.constant.DoorEventCodeEnum;
import dev.niceone.smartsiteappserver.model.constant.LockEventCodeEnum;
import dev.niceone.smartsiteappserver.model.constant.StatusCodeEnum;
import dev.niceone.smartsiteappserver.payload.*;
import dev.niceone.smartsiteappserver.payload.request.CabinetLockRequest;
import dev.niceone.smartsiteappserver.payload.request.SiteCSVRequest;
import dev.niceone.smartsiteappserver.payload.response.WifiStatusResponse;
import dev.niceone.smartsiteappserver.repository.*;
import dev.niceone.smartsiteappserver.security.CurrentUser;
import dev.niceone.smartsiteappserver.security.UserPrincipal;
import dev.niceone.smartsiteappserver.service.DeviceRebootService;
import dev.niceone.smartsiteappserver.service.SiteService;
import dev.niceone.smartsiteappserver.specification.SearchOperation;
import dev.niceone.smartsiteappserver.specification.SiteSpecification;
import dev.niceone.smartsiteappserver.specification.SpecSearchCriteria;
import lombok.extern.slf4j.Slf4j;
import net.kaczmarzyk.spring.data.jpa.domain.Equal;
import net.kaczmarzyk.spring.data.jpa.domain.LikeIgnoreCase;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Conjunction;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Or;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Spec;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static java.lang.Integer.parseInt;

@Conjunction(value = {
        @Or({@Spec(path = "name_th", params = "keyword", spec = LikeIgnoreCase.class),
                @Spec(path = "code", params = "keyword", spec = LikeIgnoreCase.class)}),
}, and = @Spec(path="region.id", params = "region", spec=Equal.class))
interface KeywordAndRegionSpec extends Specification<Site> {
}

@Spec(path="status", params = "status", spec=Equal.class)
interface EqualStatusCodeSpec extends Specification<Site> {
}

@Spec(path="active", params = "active", spec=Equal.class)
interface SiteActiveSpec extends Specification<Site> {
}

@RestController
@RequestMapping("/api/site")
@Slf4j
public class SiteController {
    private CabinetRepository cabinetRepository;
    private DeviceRepository deviceRepository;
    private DistrictRepository districtRepository;
    private ProvinceRepository provinceRepository;
    private RegionRepository regionRepository;
    private SiteRepository siteRepository;
    private SiteService siteService;
    private SubDistrictRepository subDistrictRepository;
    private WebClient webClient;

    @Autowired
    private DeviceRebootService deviceRebootService;
    @Autowired
    public SiteController(CabinetRepository cabinetRepository,
                          DeviceRepository deviceRepository,
                          DistrictRepository districtRepository,
                          ProvinceRepository provinceRepository,
                          RegionRepository regionRepository,
                          SiteRepository siteRepository,
                          SiteService siteService,
                          SubDistrictRepository subDistrictRepository,
                          WebClient.Builder webClientBuilder) {
        this.cabinetRepository = cabinetRepository;
        this.deviceRepository = deviceRepository;
        this.districtRepository = districtRepository;
        this.provinceRepository = provinceRepository;
        this.regionRepository = regionRepository;
        this.siteRepository = siteRepository;
        this.siteService = siteService;
        this.subDistrictRepository = subDistrictRepository;
        this.webClient = webClientBuilder.baseUrl("http://10.138.36.76:8089/hls").build();
    }
    @GetMapping("/version-summary")
    public Map<String, Long> getVersionSummary() {
        long android = siteRepository.countActiveAndroid();
        long pi = siteRepository.countActivePi();
        long total = siteRepository.countActive();
        long other = total - android - pi;
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("android", android);
        result.put("pi", pi);
        result.put("other", other);
        result.put("total", total);
        return result;
    }

    @GetMapping("/list")
    public PagedResponse<SiteResponse> filter4(
        @RequestParam(name="status", required=false) String status,
        @RequestParam(name="region", required=false) String region,
        @RequestParam(name="keyword", required=false) String keyword,
        @RequestParam(name="active", required=false) Boolean active,
        @RequestParam(value = "pageNumber", defaultValue = "0") int pageNumber,
        @RequestParam(value = "pageSize", defaultValue = "30") int pageSize,
        @CurrentUser UserPrincipal currentUser) {

        // Convert region string to Long if not empty
        Long regionId = null;
        if (region != null && !region.isEmpty()) {
            try {
                regionId = Long.parseLong(region);
            } catch (NumberFormatException e) {
                log.warn("Invalid region parameter: {}", region);
            }
        }

        return siteService.getAll(pageNumber, pageSize, currentUser, status, regionId, keyword, active);
    }

    // Create
    @PostMapping("/")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<?> createSite(@Valid @RequestBody SiteRequest siteRequest, MultipartFile file1) {
        ResponseEntity<ApiResponse> response = siteService.createSite(siteRequest, file1);
        return response;
    }

    // Update
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<?> saveOrUpdate(@Valid @RequestBody SiteRequest siteRequest, @PathVariable Long id, MultipartFile file1) {
        return siteService.updateSite(id, siteRequest, file1);
    }

    // Delete
    @PutMapping("/delete/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<?> deleteSite(@Valid @RequestBody SiteRequest siteRequest, @PathVariable Long id) {
        return siteService.deleteSite(id, siteRequest);
    }

    @PostMapping("/import/csv/")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.OK)
    ResponseEntity<?> importCsv(@RequestParam("file") MultipartFile file) {
        log.info("import csv");
        try (Reader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            ColumnPositionMappingStrategy<SiteCsvRequest> strategy = new ColumnPositionMappingStrategy<>();
            strategy.setType(SiteCsvRequest.class);
            String[] fields = {"is_deleted", "is_enabled", "site_code", "site_name", "region_code", "lat", "lon",
                    "ipaddr", "port", "contact_point_name", "contact_point_tel", "address", "province", "district",
                    "sub_district", "subnet_mask", "default_gateway", "installed_dt", "is_installed", "prov_id",
                    "amp_id", "dist_id", "emerg_key_success", "changed_dt"};
            strategy.setColumnMapping(fields);

            // create csv bean reader
            CsvToBean<SiteCsvRequest> csvToBean = new CsvToBeanBuilder<SiteCsvRequest>(reader)
                    .withMappingStrategy(strategy)
                    .withIgnoreLeadingWhiteSpace(true)
                    .build();

            // convert `CsvToBean` object to list of SiteCsvRequest
            List<SiteCsvRequest> siteCsvs = csvToBean.parse();

            // create Site
            List<Site> createSites = new ArrayList<>();
            List<Cabinet> createCabinets = new ArrayList<>();
            List<Device> createDevices = new ArrayList<>();
            siteCsvs.forEach(siteCsv -> {
//                log.info("-----------------------------");
                log.info("Site code: " + siteCsv.getSite_code() + "\tSite name: " + siteCsv.getSite_name() +
                        "\tRegion code:" + siteCsv.getRegion_code()+ "\tProvince: " + siteCsv.getProv_id() );
                Region region = regionRepository.findByCode(siteCsv.getRegion_code());
//                log.info("Region: " + region);
                SubDistrict subDistrict = null;
                if (!siteCsv.getDist_id().equals("null")) {
                    subDistrict = subDistrictRepository.findById(Long.parseLong(siteCsv.getDist_id())).orElse(null);
                }

//                log.info("Sub District: " + subDistrict);
                District district = null;
                Province province = null;
                if (subDistrict != null) {
                    district = subDistrict.getDistrict();
                    province = subDistrict.getDistrict().getProvince();
                } else if (siteCsv.getDistrict() != null) {
                    List<District> districtList = districtRepository.findByNameTh(siteCsv.getDistrict());
                    AtomicReference<District> tmpD = new AtomicReference<District>();
                    AtomicReference<Province> tmpP = new AtomicReference<Province>();
                    districtList.forEach(d -> {
                        if (d.getProvince().getNameTh().equals(siteCsv.getProvince())) {
                            tmpD.set(d);
                            tmpP.set(d.getProvince());
                        }
                    });
                    province = tmpP.get();
                    district = tmpD.get();
                } else if (siteCsv.getProvince() != null) {
                    Optional<Province> p = provinceRepository.findByNameTh(siteCsv.getProvince());
                    province = p.orElse(null);
                }

                Timestamp ts = Timestamp.valueOf(siteCsv.getInstalled_dt());
                LocalDateTime installedDateTime = ts.toLocalDateTime();
                boolean isInstalled = siteCsv.getIs_installed().equals("t") ? true:false;

                Site site = siteRepository.findByCode(siteCsv.getSite_code()).orElse(null);

                if (site == null) {
                    site = new Site(siteCsv.getSite_code(), siteCsv.getSite_name(),
                    region, province, district, subDistrict,
                    installedDateTime, siteCsv.getLat(), siteCsv.getLon(),
                    siteCsv.getIpaddr(), siteCsv.getPort(), siteCsv.getSubnet_mask(), siteCsv.getDefault_gateway(),
                    siteCsv.getAddress(), StatusCodeEnum.STATUS_UNKNOWN, isInstalled);

                    boolean isSiteActive = siteCsv.getIs_enabled().equals("t") ? true:false;
                    site.setActive(isSiteActive);

                    for (int i = 1; i < 4; i++) {
                        boolean isActive = false;
                        if (i==1) {
                            isActive = true;
                        }
                        Cabinet cabinet = new Cabinet(site, siteCsv.getSite_code() + "-" + i, i, isActive,
                                LockEventCodeEnum.LOCK_EVENT_UNKNOW, DoorEventCodeEnum.DOOR_EVENT_UNKNOW,"");
                        createCabinets.add(cabinet);

                        Arrays.stream(DeviceTypeCodeEnum.values()).forEach(deviceType -> {
                            Device device = new Device(cabinet, deviceType, false, false,
                                    StatusCodeEnum.STATUS_UNKNOWN);
                            createDevices.add(device);
                        });
                    }
                } else {
                    // found exist site update instead.
                    boolean isSiteActive = siteCsv.getIs_enabled().equals("t") ? true:false;
                    site.setActive(isSiteActive);

                    site.setName_th(siteCsv.getSite_name());
                    site.setRegion(region);
                    site.setProvince(province);
                    site.setDistrict(district);
                    site.setSub_district(subDistrict);
                    site.setInstalledDate(installedDateTime);
                    site.setLat(siteCsv.getLat());
                    site.setLon(siteCsv.getLon());
                    site.setIp_address(siteCsv.getIpaddr());
                    site.setPort(siteCsv.getPort());
                    site.setSubnet_mask(siteCsv.getSubnet_mask());
                    site.setDefault_gateway(siteCsv.getDefault_gateway());
                    site.setAddress(siteCsv.getAddress());
                    site.setIsInstall(isInstalled);
                }
                createSites.add(site);
            });
            log.info("<<<<<<<<<< start save all >>>>>>>>>>");
            siteRepository.saveAll(createSites);
            cabinetRepository.saveAll(createCabinets);
            deviceRepository.saveAll(createDevices);
        } catch (Exception ex) {
            ex.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse(false, ex.getMessage()));
        }
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse(true, "Import OK"));
    }

    @PostMapping("/import/csv/cabinet/")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.OK)
    ResponseEntity<?> importCsvCabinet(@RequestParam("file") MultipartFile file) {
        log.info("import csv cabinet");
        try (Reader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            ColumnPositionMappingStrategy<CabinetCsvRequest> strategy = new ColumnPositionMappingStrategy<>();
            strategy.setType(CabinetCsvRequest.class);
            String[] fields = {"is_deleted", "is_enabled", "cabinet_code", "site_code", "updated_dt", "is_installed",
                    "mac_address", "device_name"};
            strategy.setColumnMapping(fields);

            // create csv bean reader
            CsvToBean<CabinetCsvRequest> csvToBean = new CsvToBeanBuilder<CabinetCsvRequest>(reader)
                    .withMappingStrategy(strategy)
                    .withIgnoreLeadingWhiteSpace(true)
                    .build();

            List<CabinetCsvRequest> cabinetCsvs = csvToBean.parse();

            List<Cabinet> cabinets = cabinetRepository.findAll().stream().map(cabinet -> {
                cabinet.setActive(false);
                return cabinet;
            }).collect(Collectors.toList());
            log.info("<<<<<<<<<< Inactive all cabinet >>>>>>>>>>");
            cabinetRepository.saveAll(cabinets);

            cabinetCsvs.forEach(cabinetCsv -> {
                log.info("-----------------------------");
                log.info("Site code: " + cabinetCsv.getSite_code() + "\tCabinet code: " + cabinetCsv.getCabinet_code());
                Site site = siteRepository.findByCode(cabinetCsv.getSite_code()).orElse(null);
                if (site != null) {
                    Cabinet cabinet = cabinetRepository.findBySiteAndCode(site, cabinetCsv.getCabinet_code());
                    if (cabinet != null) {
                        boolean active = cabinetCsv.getIs_enabled().equals("t") ? true:false;
                        cabinet.setActive(active);

                        cabinet.setCode(cabinetCsv.getCabinet_code());

                        if (cabinetCsv.getIs_installed().equals("t")) {
                            cabinet.setUpdatedAt(LocalDateTime.now());
                            cabinet.setInstalled_date(LocalDateTime.now());
                        } else {
                            cabinet.setInstalled_date(null);
                        }

                        cabinet.setMac_address(cabinetCsv.getMac_address());
                        cabinetRepository.saveAndFlush(cabinet);
                    } else {
                        log.info("not found Cabinet.");
                    }
                } else {
                    log.info("not found Site.");
                }
            });
        } catch (Exception ex) {
            ex.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse(false, ex.getMessage()));
        }
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponse(true, "Import OK"));
    }

    // Find
    @GetMapping("/v1/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    Site findOne(@PathVariable @Min(1) Long id) {
        return siteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Site", "id", id));
    }

    @GetMapping("/v2/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    SiteResponse getSiteByIdV2(@PathVariable @Min(1) Long id) {
        return siteService.getSiteById(id);
    }

    @GetMapping("/{id}")
    SiteResponse getSiteById(@PathVariable @Min(1) Long id) {
        return siteService.getSiteById(id);
    }

    @PostMapping(value = "/healthcheck/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<?> siteHealthCheck(@PathVariable @Min(1) Long id) {
        return siteService.healthcheck(id);
    }

    @PostMapping(value = "/reboot/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<?> rebootSite(@PathVariable @Min(1) Long id,
                                 @RequestParam(value = "device_type", required = false) String deviceType) {
        Map<String, Object> result = deviceRebootService.reboot(id, deviceType);
        boolean ok = Boolean.TRUE.equals(result.get("isSuccess"));
        return ResponseEntity.status(ok ? HttpStatus.OK : HttpStatus.BAD_REQUEST).body(result);
    }

    @PostMapping(value = "/reboot/code/{code}")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<?> rebootSiteByCode(@PathVariable String code,
                                       @RequestParam(value = "device_type", required = false) String deviceType) {
        Map<String, Object> result = deviceRebootService.rebootByCode(code, deviceType);
        boolean ok = Boolean.TRUE.equals(result.get("isSuccess"));
        return ResponseEntity.status(ok ? HttpStatus.OK : HttpStatus.BAD_REQUEST).body(result);
    }

    @PostMapping(value = "/onWifi/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<?> siteOnWifi(@PathVariable @Min(1) Long id) {
        return siteService.onWifi(id);
    }

    @PostMapping(value = "/offWifi/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<?> siteOffWifi(@PathVariable @Min(1) Long id) {
        return siteService.offWifi(id);
    }

    @PostMapping(value = "/checkWifi/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<WifiStatusResponse> siteCheckWifi(@PathVariable @Min(1) Long id) {
        return siteService.checkWifi(id);
    }

    @PostMapping(value = "/startStream/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<?> startStream(@PathVariable @Min(1) Long id) {
        return siteService.startStream(id);
    }

    @PostMapping(value = "/stopStream/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<?> stopStream(@PathVariable @Min(1) Long id) {
        return siteService.stopStream(id);
    }

    @GetMapping(value = {"/stream/{urlSite}/{path:.+}","/stream/{path:.+}"})
    public void proxyStream(@PathVariable(required = false) String urlSite,@PathVariable String path, HttpServletRequest request, HttpServletResponse response) {

//        byte[] decodedBytes = Base64.getDecoder().decode(path);
//        String decodedString = new String(decodedBytes, StandardCharsets.UTF_8);
        try {
            URL url = new URL("http://10.138.36.77:8089/hls/" + path);

            if(urlSite != null && !urlSite.isEmpty()){

                byte[] urlSiteBytes = Base64.getDecoder().decode(urlSite);
                String urlSiteString = new String(urlSiteBytes, StandardCharsets.UTF_8);

                url = new URL(urlSiteString + "/" + path);
            }
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            // Copy response headers from HLS server
            for (String header : connection.getHeaderFields().keySet()) {
                if (header != null) {
                    response.setHeader(header, connection.getHeaderField(header));
                }
            }

            // Stream the data back to client
            try (InputStream inputStream = connection.getInputStream();
                 OutputStream outputStream = response.getOutputStream()) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }

        } catch (IOException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            e.printStackTrace();
        }
//        return customWebClient
//                .get()
//                .exchange() // ใช้ exchange() แทน exchangeToMono
//                .flatMap(clientResponse -> {
//                    // ตั้งค่า HTTP Headers และ Status Code
//                    response.setStatusCode(clientResponse.statusCode());
//                    clientResponse.headers().asHttpHeaders().forEach(response.getHeaders()::addAll);
//
//                    // ส่ง Stream ข้อมูลกลับไปยัง Client
//                    return response.writeWith(clientResponse.bodyToFlux(DataBuffer.class));
//                });

    }

    @PostMapping(value = "/setMotion/{id}")
    ResponseEntity<?> siteSetMotion(@PathVariable @Min(1) Long id, @RequestParam("motionTime") int motionTime) {
        return siteService.setMotion(id,motionTime);
    }

    @PostMapping(value = "/setVersion/{id}")
    ResponseEntity<?> setVersion(@PathVariable @Min(1) Long id, @RequestParam("version") String version) {
        return siteService.setVersion(id,version);
    }

    @PostMapping(value = "/setFirmwareOption/{id}")
    ResponseEntity<?> siteSetFirmwareOption(@PathVariable @Min(1) Long id, @RequestParam("firmwareOption") int firmwareOption) {
        return siteService.setFirmwareOption(id,firmwareOption);
    }

    @PostMapping(value = "/csv")
    public ResponseEntity<byte[]> generateCsvFile(@RequestBody SiteCSVRequest req){


        StringBuilder csvContent = new StringBuilder();

        csvContent.append('\uFEFF');

        csvContent.append("Site ID,Site Code,Thai Name,English Name,Region,Province,Status,Cabinet1 Code,Cabinet1 Lock,Cabinet1 Door,Cabinet2 Code,Cabinet2 Lock,Cabinet2 Door,Cabinet3 Code,Cabinet3 Lock,Cabinet3 Door,Update Date,Update Time\n");

//        String keywordParam = "";
//
//        if(req.getKeyword() != null){
//            keywordParam = req.getKeyword();
//        }
//
//        Specification<Site> spec = Specification
//                .where(new SiteSpecification(new SpecSearchCriteria("name_th", SearchOperation.LIKE, keywordParam)))
//                .and(new SiteSpecification(new SpecSearchCriteria("code", SearchOperation.LIKE, keywordParam)));
//
//        if(req.getRegion() != null){
//
//            spec = spec.and(new SiteSpecification(new SpecSearchCriteria("region.id", SearchOperation.EQUALITY, req.getRegion())));
//        }
//
//        if(req.getStatus() != null){
//            spec = spec.and(new SiteSpecification(new SpecSearchCriteria("status", SearchOperation.EQUALITY, req.getStatus())));
//        }
//
//        spec = spec.and(new SiteSpecification(new SpecSearchCriteria("active", SearchOperation.EQUALITY, true)));

        Pageable pageable = PageRequest.of(0, 1000, Sort.Direction.DESC, "id");
        
        // Build specification instead of filtering in memory
        SiteSpecification activeSpec = new SiteSpecification(new SpecSearchCriteria("active", SearchOperation.EQUALITY, true));
        Specification<Site> spec = Specification.where(activeSpec);
        
        if(req.getKeyword() != null && !req.getKeyword().isEmpty()){
            SiteSpecification nameSpec = new SiteSpecification(new SpecSearchCriteria("name_th", SearchOperation.CONTAINS, req.getKeyword()));
            SiteSpecification codeSpec = new SiteSpecification(new SpecSearchCriteria("code", SearchOperation.CONTAINS, req.getKeyword()));
            spec = spec.and(nameSpec.or(codeSpec));
        }
        
        List<Site> sites = siteRepository.findAll(spec);

        if(req.getRegion() != null && !req.getRegion().isEmpty()){
            sites = sites.stream().filter(x -> x.getRegion().getId().equals(Long.valueOf(req.getRegion()))).collect(Collectors.toList());
        }

        if(req.getStatus() != null && !req.getStatus().isEmpty()){

            int status_id;
            if(req.getStatus().contains("STATUS_DOWN")){
                status_id = 0;
            }else if(req.getStatus().contains("STATUS_UP")){
                status_id = 1;
            }else if(req.getStatus().contains("STATUS_UNKNOWN")){
                status_id = 2;
            } else {
                status_id = 2;
            }

            sites = sites.stream().filter(x -> x.getStatus().id == status_id).collect(Collectors.toList());
        }

        sites.forEach(site -> {

            Cabinet cab1 = site.getCabinets().stream().filter(x->x.getCabinetNo() == 1).findFirst().orElse(null);
            Cabinet cab2 = site.getCabinets().stream().filter(x->x.getCabinetNo()==2).findFirst().orElse(null);
            Cabinet cab3 = site.getCabinets().stream().filter(x->x.getCabinetNo()==3).findFirst().orElse(null);

            if(cab1 == null){
                cab1 = new Cabinet();
            }

            if(cab2 == null){
                cab2 = new Cabinet();
            }

            if(cab3 == null){
                cab3 = new Cabinet();
            }

            String RegionName = "";
            String ProvinceName = "";

            if(site.getRegion() != null){
                RegionName = site.getRegion().getName();
            }

            if(site.getProvince() != null){
                ProvinceName = site.getProvince().getNameTh();
            }

            DateTimeFormatter formatterDate = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            DateTimeFormatter formatterTime = DateTimeFormatter.ofPattern("HH:mm:ss");

            String upDateStr = site.getUpdatedAt().format(formatterDate); // "1986-04-08 12:30"
            String upTimeStr = site.getUpdatedAt().format(formatterTime); // "1986-04-08 12:30"

            try{
                csvContent.append(site.getId() ).append(",")
                        .append(site.getCode()).append(",")
                        .append(site.getName_th()).append(",")
                        .append(site.getName_en()).append(",")
                        .append(RegionName).append(",")
                        .append(ProvinceName).append(",")
                        .append(site.getStatus()).append(",")
                        .append(cab1.getCode()).append(",")
                        .append(LockEventCodeEnum.getById(cab1.getLockEventCode().id)).append(",")
                        .append(DoorEventCodeEnum.getById(cab1.getDoorEventCode().id)).append(",")
                        .append(cab2.getCode()).append(",")
                        .append(LockEventCodeEnum.getById(cab2.getLockEventCode().id)).append(",")
                        .append(DoorEventCodeEnum.getById(cab2.getDoorEventCode().id)).append(",")
                        .append(cab3.getCode()).append(",")
                        .append(LockEventCodeEnum.getById(cab3.getLockEventCode().id)).append(",")
                        .append(DoorEventCodeEnum.getById(cab3.getDoorEventCode().id)).append(",")
                        .append(upDateStr).append(",")
                        .append(upTimeStr).append("\n");
            }catch (Exception ei){
                log.info(ei.getMessage());
            }

        });



        HttpHeaders headers = new HttpHeaders();
       // headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.set(HttpHeaders.CONTENT_TYPE, "application/octet-stream; charset=UTF-8");
        headers.setContentDispositionFormData("attachment", "site-info.csv");

        byte[] csvBytes = csvContent.toString().getBytes(StandardCharsets.UTF_8);

        return new ResponseEntity<>(csvBytes, headers, HttpStatus.OK);
    }


    @PostMapping(value = "/csv/detail")
    public ResponseEntity<byte[]> generateCsvDetailFile(@RequestBody SiteCSVRequest req){


        StringBuilder csvContent = new StringBuilder();

        csvContent.append('\uFEFF');

        csvContent.append("Site ID,Site Code,Thai Name,English Name,Status,Region Name,Region Code,Province Name,District Name,SubDistrict Name,Zipcode,Site Key,Installed Date,Installed Time,Is Install,Updated Date,Updated Time,Active,Latitude,Longitude,IP Address,Port,Subnet Mask,Default Gateway,Address,Cabinet Total,Cabinets JSON\n");



        List<Site> sites = siteRepository.findAll();
        sites = sites.stream().filter(x -> x.getActive().equals(true)).collect(Collectors.toList());
        if(req.getKeyword() != null && !req.getKeyword().isEmpty()){
            sites = sites.stream().filter(x -> x.getName_th().contains(req.getKeyword()) || x.getCode().contains(req.getKeyword())).collect(Collectors.toList());
        }

        if(req.getRegion() != null && !req.getRegion().isEmpty()){
            sites = sites.stream().filter(x -> x.getRegion().getId().equals(Long.valueOf(req.getRegion()))).collect(Collectors.toList());
        }

        if(req.getStatus() != null && !req.getStatus().isEmpty()){

            int status_id;
            if(req.getStatus().contains("STATUS_DOWN")){
                status_id = 0;
            }else if(req.getStatus().contains("STATUS_UP")){
                status_id = 1;
            }else if(req.getStatus().contains("STATUS_UNKNOWN")){
                status_id = 2;
            } else {
                status_id = 2;
            }

            sites = sites.stream().filter(x -> x.getStatus().id == status_id).collect(Collectors.toList());
        }

        sites.forEach(site -> {

            ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
            String jsonCabinet = "";
            try {
                jsonCabinet = ow.writeValueAsString(site.getCabinets());
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }



            DateTimeFormatter formatterDate = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            DateTimeFormatter formatterTime = DateTimeFormatter.ofPattern("HH:mm:ss");

            String installDateStr = site.getInstalledDate().format(formatterDate); // "1986-04-08 12:30"
            String installTimeStr = site.getInstalledDate().format(formatterTime); // "1986-04-08 12:30"

            String upDateStr = site.getUpdatedAt().format(formatterDate); // "1986-04-08 12:30"
            String upTimeStr = site.getUpdatedAt().format(formatterTime); // "1986-04-08 12:30"


            csvContent.append(site.getId() ).append(",")
                    .append(site.getCode()).append(",")
                    .append(site.getName_th()).append(",")
                    .append(site.getName_en()).append(",")
                    .append(site.getStatus()).append(",")
                    .append(site.getRegion().getName()).append(",")
                    .append(site.getRegion().getCode()).append(",")
                    .append(site.getProvince().getNameTh()).append(",")
                    .append(site.getDistrict().getNameTh()).append(",")
                    .append(site.getSub_district().getNameTh()).append(",")
                    .append(site.getDistrict().getZipcode()).append(",")
                    .append(site.getSite_key()).append(",")
                    .append(installDateStr).append(",")
                    .append(installTimeStr).append(",")
                    .append(site.getIsInstall()).append(",")
                    .append(upDateStr).append(",")
                    .append(upTimeStr).append(",")
                    .append(site.getActive()).append(",")
                    .append(site.getLat()).append(",")
                    .append(site.getLon()).append(",")
                    .append(site.getIp_address()).append(",")
                    .append(site.getPort()).append(",")
                    .append(site.getSubnet_mask()).append(",")
                    .append(site.getDefault_gateway()).append(",")
                    .append(" ").append("\n");
        });



        HttpHeaders headers = new HttpHeaders();
        // headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.set(HttpHeaders.CONTENT_TYPE, "application/octet-stream; charset=UTF-8");
        headers.setContentDispositionFormData("attachment", "site-detail.csv");

        byte[] csvBytes = csvContent.toString().getBytes(StandardCharsets.UTF_8);

        return new ResponseEntity<>(csvBytes, headers, HttpStatus.OK);
    }

    @PostMapping(value = "/pingSite/{id}")
    ResponseEntity<?> pingSite(@PathVariable @Min(1) Long id) {
        return siteService.pingSite(id);
    }
    @PostMapping(value = "/pingSiteGateway/{id}")
    ResponseEntity<?> pingSiteGateway(@PathVariable @Min(1) Long id) {
        return siteService.pingSiteGateway(id);
    }
}
