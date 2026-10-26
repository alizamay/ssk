CREATE OR REPLACE FUNCTION smartsite.get_smart_site_config(IN p_site_code character varying)
    RETURNS TABLE(site_code character varying, site_name character varying, s_is_enabled character varying, s_is_deleted character varying, is_active character varying, installed_dt character varying, region_code character varying, region_name character varying, lat character varying, lon character varying, address character varying, province_code character varying, province_name character varying, district_code character varying, district character varying, sub_district_code character varying, sub_district character varying, emerg_key_success character varying, changed_dt character varying, contact_point_name character varying, contact_point_tel character varying, ipaddr character varying, port character varying, subnet_mask character varying, default_gateway character varying, cabinet_code character varying, cabinet_device character varying, device_name character varying, is_enabled character varying, mac_address character varying, updated_dt character varying)
    LANGUAGE 'plpgsql' VOLATILE
    COST 100    ROWS 1000
AS $BODY$
BEGIN
	--Routine body goes here...

	RETURN QUERY

SELECT		si.code as site_code,
			si.name_th as site_name,
			(Case When si.s_is_enabled Then 'True' ELse 'False' END)::varchar(5) as s_is_enabled,--si.s_is_enabled as s_is_enabled,
			(Case When si.s_is_deleted Then 'True' ELse 'False' END)::varchar(5) as s_is_deleted,--si.s_is_deleted as s_is_deleted,
			(Case When si.is_install Then 'True' ELse 'False' END)::varchar(5) as is_active,--si.is_installed as is_active,
			TO_CHAR( si.installed_date, 'MON-DD-YYYY HH12:MIPM')::varchar(25) as installed_dt,--si.installed_dt as installed_dt,
			r.code as region_code,
			r.name as region_name,
			si.lat::varchar(50) as lat,
			si.lon::varchar(50) as lon,
			si.address::varchar(1000) as address,
			CONCAT('PR', pr.id) ::varchar(10) as province_code,
			pr.name_th as province_name,
			CONCAT('AP', am.id)::varchar(50) as district_code,
			am.name_th as district,
			CONCAT('TB', d.id)::varchar(50) as sub_district_code,
			d.name_th as sub_district,
			(Case When si.emerg_key_success Then '1' ELse '0' END)::varchar(1) as emerg_key_success,
			TO_CHAR( si.updated_at, 'MON-DD-YYYY HH12:MIPM')::varchar(25) as changed_dt, --si.changed_dt as changed_dt,
			si.contact_point_name as contact_point_name,
			si.contact_point_tel as contact_point_tel,
			si.ip_address as ipaddr,
			si.port::varchar(10) as port,
			si.subnet_mask as subnet_mask,
			si.default_gateway as default_gateway,
			si.cabinet_code as cabinet_code,
			cbi.cabinet_device::varchar(100) as cabinet_device,
			CONCAT('SmartSite_', si.code)::varchar(100) as device_name,
			(Case When si.active Then 'True' ELse 'False' END)::varchar(5) as is_enabled,--si.is_enabled as is_enabled,
			si.mac_address as mac_address,
			TO_CHAR( si.updated_at, 'MON-DD-YYYY HH12:MIPM')::varchar(25) as updated_dt--si.updated_dt as updated_dt
FROM
    (
  SELECT
        s.code, s.name_th, s.active as s_is_enabled, NOT s.active as s_is_deleted, s.is_install, s.installed_date,
    s.region_id, s.lat, s.lon, s.address,
    s.province_id, s.district_id, s.sub_district_id ,
    s.site_key , s.updated_at , s.emerg_key_success,
    s.contact_point_name, s.contact_point_tel,
    s.ip_address, s.port, s.subnet_mask, s.default_gateway,
        cb.code as cabinet_code, cb.id as cabinet_id, cb.active, cb.mac_address, cb.updated_at  as cabinet_updated_at
    FROM
        smartsite.site s
    LEFT JOIN smartsite.cabinet cb ON cb.site_id = s.id
    WHERE
        s.code IN (select "site_code_t" from smartsite.string_to_rows(p_site_code) d("site_code_t"))
        --s.site_code IN ('AACBP')
    ) AS si
LEFT JOIN (
        SELECT
            sort_devices.cabinet_id,
            array_to_string(
                ARRAY_AGG ( concat (sort_devices.device_type_code, ',', sort_devices.is_install, ',', sort_devices.is_enable))
        ,'|') AS cabinet_device
        FROM
            (
				SELECT cb.site_id, d.cabinet_id, d.device_type_code, d.is_install, d.is_enable
                FROM smartsite.device d
                INNER JOIN smartsite.cabinet cb ON cb.id = d.cabinet_id
                WHERE device_type_code > 0 AND cb.site_id IN (SELECT id from smartsite.site s where s.code in (select "site_code_t" from smartsite.string_to_rows(p_site_code) d("site_code_t")))
                ORDER BY cabinet_id, device_type_code ASC
			) AS sort_devices
        GROUP BY
            sort_devices.cabinet_id

) AS cbi
ON si.cabinet_id = cbi.cabinet_id
LEFT JOIN smartsite.region r
ON r.id = si.region_id
LEFT JOIN smartsite.province pr
ON pr.id = si.province_id
LEFT JOIN smartsite.district am
ON am.id = si.district_id
LEFT JOIN smartsite.sub_district d
ON d.id = si.sub_district_id order by cbi.cabinet_id asc;

END;
$BODY$;