CREATE OR REPLACE FUNCTION public.get_smart_site_info(IN p_site_code character varying)
    RETURNS TABLE(site_code character varying, cabinet_code character varying, c_installed character varying, json_cabinet_name character varying, site_last_updated_dt character varying, cabinet_last_updated_dt character varying, device_last_updated_dt character varying, device_type_id character varying, device_type character varying, json_device_type character varying, json_installed character varying, json_enabled character varying, json_online character varying, status_name character varying, json_status character varying, event_type_id character varying, event_name character varying, site_name character varying, region_code character varying, region_name character varying, lat character varying, lon character varying, address character varying, province character varying, district character varying, sub_district character varying, ipaddr character varying, port character varying, subnet_mask character varying, default_gateway character varying, installed_dt character varying, prov_id character varying, amp_id character varying, dist_id character varying)
    LANGUAGE 'plpgsql' VOLATILE
    COST 100    ROWS 1000
AS $BODY$
BEGIN
	--Routine body goes here...
		RETURN QUERY
SELECT
        s.code ::varchar(50),
        c.code ::varchar(50) AS cabinet_code,
    CASE
        WHEN c.active THEN 1
        ELSE 0
        END ::varchar(50) AS c_installed,
    CASE
        WHEN c.code LIKE '%-1' THEN 'c1'
        WHEN c.code LIKE '%-2' THEN 'c2'
        WHEN c.code LIKE '%-3' THEN 'c3' end ::varchar(50) AS json_cabinet_name,
    s.updated_at ::varchar(50) AS site_last_updated_dt,
	c.updated_at ::varchar(50) AS cabinet_last_updated_dt,
    d.updated_at  ::varchar(50) AS device_last_updated_dt,
    d.device_type_code ::varchar(50) ,
    CASE
        WHEN d.device_type_code = 1 THEN 'Smart Lock'
        WHEN d.device_type_code = 10 THEN 'Key Switch'
        WHEN d.device_type_code = 2 THEN 'Buzzer'
        WHEN d.device_type_code = 3 THEN 'MIPI Camera'
        WHEN d.device_type_code = 4 THEN 'IP Cam'
        WHEN d.device_type_code = 5 THEN 'Motion Sensor'
        WHEN d.device_type_code = 6 THEN 'Vibration Sensor'
        WHEN d.device_type_code = 7 THEN 'Door Switch'
    	WHEN d.device_type_code = 8 THEN 'SD Card'
    	WHEN d.device_type_code = 9 THEN 'Slave Controller' END ::varchar(50) AS device_type,
	CASE
        WHEN d.device_type_code = 1 THEN 'lock'
        WHEN d.device_type_code = 10 THEN 'keysw'
        WHEN d.device_type_code = 2 THEN 'buzzer'
        WHEN d.device_type_code = 3 THEN 'pincam'
        WHEN d.device_type_code = 4 THEN 'ipcam'
        WHEN d.device_type_code = 5 THEN 'motion'
        WHEN d.device_type_code = 6 THEN 'vibrat'
        WHEN d.device_type_code = 7 THEN 'door'
    	WHEN d.device_type_code = 8 THEN 'sdcard'
    	WHEN d.device_type_code = 9 THEN 'slave' END ::varchar(50) AS json_device_type,
    CASE WHEN d.is_install THEN 1 ELSE 0 end ::varchar(50)  AS json_installed,
    CASE WHEN d.is_enable THEN 1 ELSE 0 END ::varchar(50) AS json_enabled,
    CASE
        WHEN d.status = 0 THEN 0
        WHEN d.status = 1 THEN 1
        ELSE 2 END ::varchar(50) AS json_online,
	CASE
        WHEN d.status = 0 THEN 'Offline'
        WHEN d.status = 1 THEN 'Online'
		WHEN d.status = 2 THEN 'Unknown' END ::varchar(50) AS status_name,
    CASE
        WHEN d.event_type = 0 THEN 1
        WHEN d.event_type = 1 THEN 1
        WHEN d.event_type = 2 THEN 0
        WHEN d.event_type = 3 THEN 0
        WHEN d.event_type = 4 THEN 0
        WHEN d.event_type = 5 THEN 0
        WHEN d.event_type = 6 THEN 1
        WHEN d.event_type = 7 THEN 0
        WHEN d.event_type = 8 THEN 0
        WHEN d.event_type = 9 THEN 0
        WHEN d.event_type = 10 THEN 1
        WHEN d.event_type = 11 THEN 1
        ELSE 2 END ::varchar(50) AS json_status,
        CASE
            WHEN d.event_type is null THEN 2
        ELSE d.event_type END ::varchar(50) AS event_type_id,
        CASE
            WHEN d.event_type is null THEN 'Unlocked'
			when d.event_type = 0 THEN 'No event'
			when d.event_type = 1 THEN 'Locked'
			when d.event_type = 2 THEN 'Unlocked'
			when d.event_type = 3 THEN 'Motion detected'
			when d.event_type = 4 THEN 'Vibration detected'
			when d.event_type = 5 THEN 'Siren ringing'
			when d.event_type = 6 THEN 'Door closed'
			when d.event_type = 7 THEN 'Door opened'
			when d.event_type = 8 THEN 'Full Memory'
			when d.event_type = 9 THEN 'Slave Controller Down'
			when d.event_type = 10 THEN 'Key Locked'
			when d.event_type = 11 THEN 'Key Unlocked' END ::varchar(50) AS event_name ,
    s.name_th ::varchar(50) ,
    r.code ::varchar(50) ,
    r.name ::varchar(50) ,
    s.lat ::varchar(50) as lat,
    s.lon ::varchar(50) as lon,
    s.address ::varchar(50),
    p.name_th ::varchar(50)   as province,
    amp.name_th  ::varchar(50)   as district,
    dist.name_th ::varchar(50)  as sub_district,
    s.ip_address ::varchar(50) ,
    s.port ::varchar(50) ,
    s.subnet_mask  ::varchar(50),
    s.default_gateway  ::varchar(50),
    s.installed_date  ::varchar(50),
    cast(s.province_id as varchar(10))::varchar(50) prov_id,
    cast(s.district_id as varchar(10)) ::varchar(50) amp_id,
    cast(s.sub_district_id as varchar(10))::varchar(50) dist_id
FROM public.site s
      LEFT JOIN public.region r ON s.region_id = r.id
      LEFT JOIN public.province p ON s.province_id = p.id
      LEFT JOIN public.district amp ON s.district_id = amp.id
      LEFT JOIN public.sub_district dist ON s.sub_district_id = dist.id
      LEFT JOIN public.cabinet c ON c.site_id = s.id                 
      LEFT JOIN public.device d ON d.cabinet_id = c.id                 
      --LEFT JOIN device_status_current dsc ON c.cabinet_code = dsc.cabinet_code AND d.device_type_id = dsc.device_type_id
WHERE s.active = true
AND d.device_type_code IN (1, 2, 3, 4, 5, 6, 7,8,9, 10)
AND s.code = p_site_code
ORDER BY c.code,d.device_type_code;
END;
$BODY$;