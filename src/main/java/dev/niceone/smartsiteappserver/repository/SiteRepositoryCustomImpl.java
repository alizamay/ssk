package dev.niceone.smartsiteappserver.repository;

import dev.niceone.smartsiteappserver.model.constant.StatusCodeEnum;
import dev.niceone.smartsiteappserver.payload.SiteResponse;
import dev.niceone.smartsiteappserver.payload.StatusResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Repository
public class SiteRepositoryCustomImpl implements SiteRepositoryCustom {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final RowMapper<SiteResponse> siteResponseMapper = new RowMapper<SiteResponse>() {
        @Override
        public SiteResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
            SiteResponse response = new SiteResponse();
            
            // Basic site info
            response.setId(rs.getLong("site_id"));
            response.setCode(rs.getString("site_code"));
            response.setName_th(rs.getString("site_name_en"));
            response.setName_en(rs.getString("site_name_en"));
            response.setSite_key(rs.getString("site_key"));
            response.setActive(rs.getBoolean("site_active"));
            response.setIsInstall(rs.getBoolean("site_is_install"));
            response.setUpdatedAt(rs.getTimestamp("site_updated_at") != null ? 
                rs.getTimestamp("site_updated_at").toLocalDateTime() : null);
            response.setInstalled_date(rs.getTimestamp("site_installed_date") != null ?
                rs.getTimestamp("site_installed_date").toLocalDateTime() : null);
            
            // Location info
            response.setLat(rs.getString("site_lat"));
            response.setLon(rs.getString("site_lon"));
            response.setAddress(rs.getString("site_address"));
            
            // Network info
            response.setIp_address(rs.getString("site_ip_address"));
            response.setPort(rs.getInt("site_port"));
            response.setSubnet_mask(rs.getString("site_subnet_mask"));
            response.setDefault_gateway(rs.getString("site_default_gateway"));
            
            // Status
            StatusCodeEnum statusCode = StatusCodeEnum.values()[rs.getInt("site_status")];
            StatusResponse statusResponse = new StatusResponse();
            statusResponse.setId(statusCode.id);
            statusResponse.setCode(statusCode.code);
            statusResponse.setName(statusCode.name);
            response.setStatus(statusResponse);
            
            // Region info
            response.setRegion_id(rs.getLong("region_id"));
            response.setRegion_code(rs.getString("region_code"));
            response.setRegion_name(rs.getString("region_name"));
            
            // Province info
            Long provinceId = rs.getLong("province_id");
            if (!rs.wasNull()) {
                response.setProvince_id(provinceId);
                response.setProvince_code(rs.getString("province_code"));
                response.setProvince_name_th(rs.getString("province_name_th"));
                response.setProvince_name_en(rs.getString("province_name_en"));
            }
            
            // District info  
            Long districtId = rs.getLong("district_id");
            if (!rs.wasNull()) {
                response.setDistrict_id(districtId);
                response.setDistrict_name_th(rs.getString("district_name_th"));
                response.setDistrict_name_en(rs.getString("district_name_en"));
                response.setDistrict_zipcode(rs.getInt("district_zipcode"));
            }
            
            // Sub District info
            Long subDistrictId = rs.getLong("sub_district_id");
            if (!rs.wasNull()) {
                response.setSub_district_id(subDistrictId);
                response.setSub_district_name_th(rs.getString("sub_district_name_th"));
                response.setSub_district_name_en(rs.getString("sub_district_name_en"));
            }
            
            // Additional fields
            response.setTemperature(rs.getDouble("site_temperature"));
            response.setVersion_number(rs.getString("site_version"));
            response.setMotion(rs.getInt("site_motion"));
            response.setFirmware_option(rs.getInt("site_firmware_option"));
            
            // Skip cabinets for performance - set empty list
            response.setCabinets(Collections.emptyList());
            response.setCabinet_count(0);
            response.setVersion(null);
            
            return response;
        }
    };

    @Override
    public List<SiteResponse> findSitesForUserOptimized(Long userId, int page, int size, String status, Long regionId, String keyword, Boolean active) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ")
            .append("s.id as site_id, ")
            .append("s.code as site_code, ")
            .append("s.name_th as site_name_th, ")
            .append("s.name_en as site_name_en, ")
            .append("s.site_key as site_key, ")
            .append("s.active as site_active, ")
            .append("s.is_install as site_is_install, ")
            .append("s.updated_at as site_updated_at, ")
            .append("s.installed_date as site_installed_date, ")
            .append("s.lat as site_lat, ")
            .append("s.lon as site_lon, ")
            .append("s.address as site_address, ")
            .append("s.ip_address as site_ip_address, ")
            .append("s.port as site_port, ")
            .append("s.subnet_mask as site_subnet_mask, ")
            .append("s.default_gateway as site_default_gateway, ")
            .append("s.status as site_status, ")
            .append("s.temperature as site_temperature, ")
            .append("s.version as site_version, ")
            .append("s.motion as site_motion, ")
            .append("s.firmware_option as site_firmware_option, ")
            .append("r.id as region_id, ")
            .append("r.code as region_code, ")
            .append("r.name as region_name, ")
            .append("p.id as province_id, ")
            .append("p.code as province_code, ")
            .append("p.name_th as province_name_th, ")
            .append("p.name_en as province_name_en, ")
            .append("d.id as district_id, ")
            .append("d.name_th as district_name_th, ")
            .append("d.name_en as district_name_en, ")
            .append("d.zipcode as district_zipcode, ")
            .append("sd.id as sub_district_id, ")
            .append("sd.name_th as sub_district_name_th, ")
            .append("sd.name_en as sub_district_name_en ")
            .append("FROM smartsite.site s ")
            .append("LEFT JOIN smartsite.region r ON s.region_id = r.id ")
            .append("LEFT JOIN smartsite.province p ON s.province_id = p.id ")
            .append("LEFT JOIN smartsite.district d ON s.district_id = d.id ")
            .append("LEFT JOIN smartsite.sub_district sd ON s.sub_district_id = sd.id ")
            .append("WHERE (")
            .append("EXISTS (")
            .append("SELECT 1 FROM smartsite.user_regions ur ")
            .append("WHERE ur.user_id = ? AND ur.region_id = s.region_id")
            .append(") ")
            .append("OR EXISTS (")
            .append("SELECT 1 FROM smartsite.user_sites us ")
            .append("WHERE us.user_id = ? AND us.site_id = s.id")
            .append(")) ");

        // Build dynamic WHERE clause with filters
        List<Object> params = new java.util.ArrayList<>();
        params.add(userId);
        params.add(userId);

        // Filter by active status
        if (active != null) {
            sql.append("AND s.active = ? ");
            params.add(active);
        }

        // Filter by status
        if (status != null && !status.isEmpty()) {
            int statusId;
            if (status.equals("STATUS_DOWN")) {
                statusId = 0;
            } else if (status.equals("STATUS_UP")) {
                statusId = 1;
            } else if (status.equals("STATUS_UNKNOWN")) {
                statusId = 2;
            } else {
                statusId = -1; // Invalid status
            }

            if (statusId >= 0) {
                sql.append("AND s.status = ? ");
                params.add(statusId);
            }
        }

        // Filter by region
        if (regionId != null) {
            sql.append("AND s.region_id = ? ");
            params.add(regionId);
        }

        // Filter by keyword (search in name_th or code)
        if (keyword != null && !keyword.isEmpty()) {
            sql.append("AND (LOWER(s.name_th) LIKE LOWER(?) OR LOWER(s.code) LIKE LOWER(?)) ");
            String keywordPattern = "%" + keyword + "%";
            params.add(keywordPattern);
            params.add(keywordPattern);
        }

        sql.append("ORDER BY s.id DESC ")
            .append("LIMIT ? OFFSET ?");

        int offset = page * size;
        params.add(size);
        params.add(offset);

        return jdbcTemplate.query(sql.toString(), siteResponseMapper, params.toArray());
    }

    @Override
    public long countSitesForUser(Long userId, String status, Long regionId, String keyword, Boolean active) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(DISTINCT s.id) ")
            .append("FROM smartsite.site s ")
            .append("WHERE (")
            .append("EXISTS (")
            .append("SELECT 1 FROM smartsite.user_regions ur ")
            .append("WHERE ur.user_id = ? AND ur.region_id = s.region_id")
            .append(") ")
            .append("OR EXISTS (")
            .append("SELECT 1 FROM smartsite.user_sites us ")
            .append("WHERE us.user_id = ? AND us.site_id = s.id")
            .append(")) ");

        // Build dynamic WHERE clause with filters
        List<Object> params = new java.util.ArrayList<>();
        params.add(userId);
        params.add(userId);

        // Filter by active status
        if (active != null) {
            sql.append("AND s.active = ? ");
            params.add(active);
        }

        // Filter by status
        if (status != null && !status.isEmpty()) {
            int statusId;
            if (status.equals("STATUS_DOWN")) {
                statusId = 0;
            } else if (status.equals("STATUS_UP")) {
                statusId = 1;
            } else if (status.equals("STATUS_UNKNOWN")) {
                statusId = 2;
            } else {
                statusId = -1; // Invalid status
            }

            if (statusId >= 0) {
                sql.append("AND s.status = ? ");
                params.add(statusId);
            }
        }

        // Filter by region
        if (regionId != null) {
            sql.append("AND s.region_id = ? ");
            params.add(regionId);
        }

        // Filter by keyword (search in name_th or code)
        if (keyword != null && !keyword.isEmpty()) {
            sql.append("AND (LOWER(s.name_th) LIKE LOWER(?) OR LOWER(s.code) LIKE LOWER(?)) ");
            String keywordPattern = "%" + keyword + "%";
            params.add(keywordPattern);
            params.add(keywordPattern);
        }

        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return count != null ? count : 0L;
    }

    @Override
    public void loadCabinetsForSites(List<SiteResponse> siteResponses) {
        if (siteResponses == null || siteResponses.isEmpty()) {
            return;
        }

        // Collect all site IDs
        List<Long> siteIds = siteResponses.stream()
                .map(SiteResponse::getId)
                .collect(java.util.stream.Collectors.toList());

        if (siteIds.isEmpty()) {
            return;
        }

        // Build placeholders for IN clause
        String siteIdPlaceholders = siteIds.stream()
                .map(id -> "?")
                .collect(java.util.stream.Collectors.joining(","));

        // 1. Load Site Versions
        loadSiteVersions(siteResponses, siteIds, siteIdPlaceholders);

        // 2. Query all cabinets for these sites
        String cabinetSql = "SELECT " +
                "c.id as cabinet_id, " +
                "c.code as cabinet_code, " +
                "c.cabinet_no, " +
                "c.online, " +
                "c.lock_event_code, " +
                "c.door_event_code, " +
                "c.active as cabinet_active, " +
                "c.auxin1, " +
                "c.auxin2, " +
                "c.auxin3, " +
                "c.auxout, " +
                "c.site_id " +
                "FROM smartsite.cabinet c " +
                "WHERE c.site_id IN (" + siteIdPlaceholders + ") " +
                "ORDER BY c.site_id, c.cabinet_no";

        // Map to store cabinets by site_id and cabinet_id
        Map<Long, List<dev.niceone.smartsiteappserver.payload.CabinetResponse>> cabinetsBySiteId = new java.util.HashMap<>();
        Map<Long, dev.niceone.smartsiteappserver.payload.CabinetResponse> cabinetsById = new java.util.HashMap<>();

        jdbcTemplate.query(cabinetSql, rs -> {
            Long siteId = rs.getLong("site_id");
            Long cabinetId = rs.getLong("cabinet_id");

            dev.niceone.smartsiteappserver.payload.CabinetResponse cabinet =
                    new dev.niceone.smartsiteappserver.payload.CabinetResponse();
            cabinet.setId(cabinetId);
            cabinet.setCabinet_code(rs.getString("cabinet_code"));
            cabinet.setCabinet_no(rs.getInt("cabinet_no"));
            cabinet.setOnline(rs.getBoolean("online"));

            int lockCode = rs.getInt("lock_event_code");
            cabinet.setLockEventCode(getLockEventName(lockCode));

            int doorCode = rs.getInt("door_event_code");
            cabinet.setDoorEventCode(getDoorEventName(doorCode));

            cabinet.setActive(rs.getBoolean("cabinet_active"));
            cabinet.setAuxin1(rs.getBoolean("auxin1"));
            cabinet.setAuxin2(rs.getBoolean("auxin2"));
            cabinet.setAuxin3(rs.getBoolean("auxin3"));
            cabinet.setAuxout(rs.getBoolean("auxout"));

            // Initialize empty lists
            cabinet.setDevices(new java.util.ArrayList<>());
            cabinet.setAux_devices(new java.util.ArrayList<>());

            cabinetsBySiteId.computeIfAbsent(siteId, k -> new java.util.ArrayList<>()).add(cabinet);
            cabinetsById.put(cabinetId, cabinet);
        }, siteIds.toArray());

        // 3. Load Devices for all cabinets
        if (!cabinetsById.isEmpty()) {
            loadDevicesForCabinets(cabinetsById);
            loadAuxDevicesForCabinets(cabinetsById);
        }

        // 4. Assign cabinets to their respective sites
        for (SiteResponse site : siteResponses) {
            List<dev.niceone.smartsiteappserver.payload.CabinetResponse> cabinets =
                    cabinetsBySiteId.getOrDefault(site.getId(), new java.util.ArrayList<>());
            site.setCabinets(cabinets);
            site.setCabinet_count(cabinets.size());
        }
    }

    private void loadSiteVersions(List<SiteResponse> siteResponses, List<Long> siteIds, String placeholders) {
        String versionSql = "SELECT site_id, version_name FROM smartsite.site_version " +
                "WHERE site_id IN (" + placeholders + ") AND active = true";

        Map<Long, String> versionsBySiteId = new java.util.HashMap<>();
        jdbcTemplate.query(versionSql, rs -> {
            versionsBySiteId.put(rs.getLong("site_id"), rs.getString("version_name"));
        }, siteIds.toArray());

        for (SiteResponse site : siteResponses) {
            site.setVersion(versionsBySiteId.get(site.getId()));
        }
    }

    private void loadDevicesForCabinets(Map<Long, dev.niceone.smartsiteappserver.payload.CabinetResponse> cabinetsById) {
        List<Long> cabinetIds = new java.util.ArrayList<>(cabinetsById.keySet());
        String cabinetIdPlaceholders = cabinetIds.stream()
                .map(id -> "?")
                .collect(java.util.stream.Collectors.joining(","));

        String deviceSql = "SELECT " +
                "d.id as device_id, " +
                "d.device_type_code, " +
                "d.is_enable, " +
                "d.is_install, " +
                "d.status, " +
                "d.cabinet_id " +
                "FROM smartsite.device d " +
                "WHERE d.cabinet_id IN (" + cabinetIdPlaceholders + ") " +
                "ORDER BY d.cabinet_id, d.device_type_code";

        jdbcTemplate.query(deviceSql, rs -> {
            Long cabinetId = rs.getLong("cabinet_id");
            dev.niceone.smartsiteappserver.payload.CabinetResponse cabinet = cabinetsById.get(cabinetId);

            if (cabinet != null) {
                dev.niceone.smartsiteappserver.payload.DeviceResponse device =
                        new dev.niceone.smartsiteappserver.payload.DeviceResponse();
                device.setId(rs.getLong("device_id"));

                int deviceTypeCode = rs.getInt("device_type_code");
                device.setType_id(deviceTypeCode);
                device.setType_code(getDeviceTypeCode(deviceTypeCode));
                device.setType_name(getDeviceTypeName(deviceTypeCode));

                device.setIs_enable(rs.getBoolean("is_enable"));
                device.setIs_install(rs.getBoolean("is_install"));

                int statusCode = rs.getInt("status");
                dev.niceone.smartsiteappserver.payload.StatusResponse statusResponse =
                        new dev.niceone.smartsiteappserver.payload.StatusResponse();
                statusResponse.setId(statusCode);
                statusResponse.setCode(getStatusCodeName(statusCode));
                statusResponse.setName(getStatusCodeName(statusCode));
                device.setStatus(statusResponse);

                cabinet.getDevices().add(device);
            }
        }, cabinetIds.toArray());
    }

    private void loadAuxDevicesForCabinets(Map<Long, dev.niceone.smartsiteappserver.payload.CabinetResponse> cabinetsById) {
        List<Long> cabinetIds = new java.util.ArrayList<>(cabinetsById.keySet());
        String cabinetIdPlaceholders = cabinetIds.stream()
                .map(id -> "?")
                .collect(java.util.stream.Collectors.joining(","));

        String auxDeviceSql = "SELECT " +
                "ad.id as aux_device_id, " +
                "ad.aux_no, " +
                "ad.name, " +
                "ad.is_active, " +
                "ad.is_input, " +
                "ad.is_on, " +
                "ad.cabinet_id " +
                "FROM smartsite.aux_device ad " +
                "WHERE ad.cabinet_id IN (" + cabinetIdPlaceholders + ") " +
                "ORDER BY ad.cabinet_id, ad.aux_no";

        jdbcTemplate.query(auxDeviceSql, rs -> {
            Long cabinetId = rs.getLong("cabinet_id");
            dev.niceone.smartsiteappserver.payload.CabinetResponse cabinet = cabinetsById.get(cabinetId);

            if (cabinet != null) {
                dev.niceone.smartsiteappserver.payload.response.AuxResponse auxDevice =
                        new dev.niceone.smartsiteappserver.payload.response.AuxResponse();
                auxDevice.setId(rs.getLong("aux_device_id"));
                auxDevice.setAux_no(rs.getInt("aux_no"));
                auxDevice.setAux_name(rs.getString("name"));
                auxDevice.setIs_active(rs.getBoolean("is_active"));
                auxDevice.setIs_input(rs.getBoolean("is_input"));
                auxDevice.setIs_on(rs.getBoolean("is_on"));

                cabinet.getAux_devices().add(auxDevice);
            }
        }, cabinetIds.toArray());
    }

    private String getLockEventName(int lockCode) {
        switch (lockCode) {
            case 0: return "LOCK_EVENT_LOCKED";
            case 1: return "LOCK_EVENT_UNLOCKED";
            case 2: return "LOCK_EVENT_UNKNOW";
            default: return "LOCK_EVENT_LOCKED";
        }
    }

    private String getDoorEventName(int doorCode) {
        switch (doorCode) {
            case 0: return "DOOR_EVENT_OPENED";
            case 1: return "DOOR_EVENT_CLOSED";
            case 2: return "DOOR_EVENT_UNKNOW";
            default: return "DOOR_EVENT_CLOSED";
        }
    }

    private String getStatusCodeName(int statusCode) {
        switch (statusCode) {
            case 0: return "STATUS_DOWN";
            case 1: return "STATUS_UP";
            case 2: return "STATUS_UNKNOWN";
            default: return "STATUS_UNKNOWN";
        }
    }

    private String getDeviceTypeCode(int deviceTypeCode) {
        switch (deviceTypeCode) {
            case 0: return "LOCK";
            case 1: return "BATTERY";
            case 2: return "MAIN_POWER";
            case 3: return "DOOR";
            case 4: return "BUZZER";
            case 5: return "LED";
            case 6: return "CAMERA";
            case 7: return "TEMPERATURE";
            default: return "UNKNOWN";
        }
    }

    private String getDeviceTypeName(int deviceTypeCode) {
        switch (deviceTypeCode) {
            case 0: return "Lock";
            case 1: return "Battery";
            case 2: return "Main Power";
            case 3: return "Door";
            case 4: return "Buzzer";
            case 5: return "LED";
            case 6: return "Camera";
            case 7: return "Temperature";
            default: return "Unknown";
        }
    }
}