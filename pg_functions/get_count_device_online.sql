CREATE OR REPLACE FUNCTION public.get_count_device_online(IN s_code character varying)
    RETURNS TABLE(count_json_installed character varying)
    LANGUAGE 'plpgsql' VOLATILE
    COST 100    ROWS 1000
AS $BODY$
BEGIN
	--Routine body goes here...
		RETURN QUERY

		SELECT COALESCE(sum(CASE WHEN d.status = 1 OR d.device_type_code = 9 THEN 1 ELSE 0 end),0) ::varchar(50)   AS count_json_installed 	
		 FROM public.site s 
		  LEFT JOIN public.cabinet c ON s.id = c.site_id 				 
		  LEFT JOIN public.device d ON c.id = d.cabinet_id				 
		 WHERE s.active = true 
		 AND d.device_type_code IN (1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
		 AND s.code = s_code
		 AND c.active = true 
		 AND d.is_install = true 
		 AND d.is_enable = true 
		 --AND dsc.status_type_id = 1		
		;
		
END;
$BODY$;