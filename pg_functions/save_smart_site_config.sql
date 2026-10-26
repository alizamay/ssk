CREATE OR REPLACE FUNCTION smartsite.save_smart_site_config(IN p_site_code character varying, IN p_site_name character varying, IN p_region_code character varying, IN p_lat real, IN p_lon real, IN p_ipaddr character varying, IN p_port integer, IN p_address character varying, IN p_subnet_mask character varying, IN p_default_gateway character varying, IN p_province_code character varying, IN p_district_code character varying, IN p_sub_district_code character varying, IN p_cabinet_1 character varying, IN p_cabinet_2 character varying, IN p_cabinet_3 character varying)
    RETURNS void
    LANGUAGE 'plpgsql'
    VOLATILE
    COST 100
AS $BODY$
DECLARE

    v_count_site integer;
    v_count_cabinet integer;
    v_count_device integer;
    v_count_device_enable integer := 0;
    v_count_cabinet_enable integer := 0;
    v_province_id integer;
    v_district_id integer;
    v_sub_district_id integer;
    v_device_name character varying := 'SmartSite_'||p_site_code;
    --v_cabinet_code_1 character varying := p_site_code||'-1';
    --v_cabinet_code_2 character varying := p_site_code||'-2';
    --v_cabinet_code_3 character varying := p_site_code||'-3';
    v_cabinet_code character varying[] DEFAULT ARRAY[p_site_code||'-1', p_site_code||'-2', p_site_code||'-3'];
    v_cabinet character varying[] DEFAULT ARRAY[p_cabinet_1, p_cabinet_2, p_cabinet_3];
    v_cabinet_device text[];
    --v_cabinet_1 text[];
    --v_cabinet_2 text[];
    --v_cabinet_3 text[];

    v_device_info character varying[];

	v_serial_repair_status character varying;
	v_return_status character varying;
	v_repair_status character varying;
    v_region_id integer;
    v_region_name character varying;
    v_site_id integer;
    tmp_cabinet_id integer;

BEGIN

    -- ## 1 CHECK SITE INFORMATION ## --

    SELECT COUNT(1) INTO v_count_site
    FROM smartsite.site s
    WHERE s.code ilike p_site_code
    ;

    SELECT p.id
    INTO v_province_id
    FROM smartsite.province p
    WHERE p.id = CAST (REPLACE(p_province_code, 'PR', '') AS INTEGER)
    ;

    IF p_district_code is not NULL THEN
	    SELECT a.id::integer
	    INTO v_district_id
	    FROM smartsite.district a
	    WHERE a.id::integer = CAST (REPLACE(p_district_code, 'AP', '') AS INTEGER)
	    ;
	END IF;

    IF p_sub_district_code is not NULL THEN
	    SELECT d.id
	    INTO v_sub_district_id
	    FROM smartsite.sub_district d
	    WHERE d.id = CAST (REPLACE(p_sub_district_code, 'TB', '') AS INTEGER)
	    ;
	END IF;

    IF v_province_id is NULL THEN
        RAISE EXCEPTION 'Province Code: (%) is not found.',p_province_code;
    --ELSEIF v_district_id is NULL THEN
    --    RAISE EXCEPTION 'District Code: (%) is not found.',p_district_code;
	--ELSEIF v_sub_district_id is NULL THEN
    --    RAISE EXCEPTION 'Sub District Code: (%) is not found.',p_sub_district_code;
	END IF;

	SELECT r.id, r.name
    INTO v_region_id, v_region_name
    FROM smartsite.region r
    WHERE r.code = p_region_code --'BKK'
    ;


    IF v_count_site > 0 THEN
    	-- ## CASE UPDATE SITE INFORMATION ## --
    	RAISE INFO 'UPDATE SITE INFORMATION: %',p_site_code;
        UPDATE smartsite.site
        SET "active"='t',
			"name_th" = p_site_name,
			"name_en" = p_site_name,
			"region_id" = v_region_id,
			"lat"= p_lat,
			"lon" = p_lon,
			"ip_address" = p_ipaddr,
			"port" = p_port,
			--"contact_point_name" = NULL,
			--"contact_point_tel" = NULL,
			"address" = p_address,
			"province_id" = v_province_id,
			"district_id" = v_district_id,
			"sub_district_id" = v_sub_district_id,
			"subnet_mask" = p_subnet_mask,
			"default_gateway" = p_default_gateway,
			--"installed_dt" = now(),
			--"emerg_key_success" = NULL, TODO later
			"updated_at" = now()
		WHERE "code" = p_site_code;
    ELSE
		-- ## CASE ADD NEW SITE INFORMATION ## --
    	RAISE INFO 'ADD NEW SITE INFORMATION: %',p_site_code;
		INSERT INTO smartsite.site (
			 "active",
			 "code",
			 "name_th",
			 "name_en",
			 "region_id",
			 "lat",
			 "lon",
			 "ip_address",
			 "port",
			 "contact_point_name",
			 "contact_point_tel",
			 "address",
			 "province_id",
			 "district_id",
			 "sub_district_id",
			 "subnet_mask",
			 "default_gateway",
			 "installed_date",
			 --"emerg_key_success", TODO later
			 "created_at",
			 "updated_at",
			 "site_key",
			 "status"
		)
		VALUES (
				 't',
				 p_site_code,--'BHAKM',
				 p_site_name,--'Bang Hak',
				 p_site_name,--'Bang Hak',
				 v_region_id,--'ER',
				 p_lat,--13.5458,
				 p_lon,--101.063,
				 p_ipaddr,--'10.242.112.128',
				 p_port,--8080,
				 NULL,
				 NULL,
				 p_address,
				 v_province_id,
				 v_district_id,
				 v_sub_district_id,
				 p_subnet_mask,--'',
				 p_default_gateway,--'',
				 now(),
				 now(),
				 --NULL, TODO emerg
				 now(),
				 '0000', --initial site_key
				 2
		);
    END IF;

    -- Get site_id after create or update
    SELECT s.id INTO v_site_id
    FROM smartsite.site s
    WHERE s.code ilike p_site_code
    ;

    -- ## 2 CHECK CABINET CONFIGURATION ## --
    v_count_cabinet_enable = 3;

	FOR j IN array_lower(v_cabinet, 1) .. array_upper(v_cabinet, 1) LOOP
		RAISE INFO 'CABINET: %',j;

		v_count_device_enable = 0;

		SELECT COUNT(1) INTO v_count_cabinet
		FROM smartsite.cabinet cb
		--WHERE cb.cabinet_code = v_cabinet_code_1 and "site_code" = p_site_code;
		WHERE cb.code = v_cabinet_code[j] and "site_id" = v_site_id;

		IF v_cabinet[j] is NOT NULL AND v_cabinet[j] != '' THEN
			--IF p_cabinet_1 is NOT NULL THEN

			IF v_count_cabinet > 0 THEN
				-- ## CASE UPDATE CABINET CONFIGURATION ## --
				RAISE INFO 'UPDATE CABINET: %',v_cabinet_code[j];
				UPDATE smartsite.cabinet
				SET "active" = 't',
					"updated_at" = now(),
					"mac_address" = 'NO_DATA'
				--WHERE "cabinet_code" = v_cabinet_code_1 and "site_code" = p_site_code;
				WHERE "code" = v_cabinet_code[j] and "site_id" = v_site_id;
			ELSE
				-- ## CASE ADD NEW CABINET CONFIGURATION  ## --
				RAISE INFO 'ADD NEW CABINET: %',v_cabinet_code[j];
				INSERT INTO smartsite.cabinet (
					"active",
					"cabinet_no",
					"code",
					"site_id",
					"updated_at",
					"installed_date",
					"mac_address",
					"created_at",
					"door_event_code",
					"lock_event_code"
				)
				VALUES
					(
						't',
						j,
						v_cabinet_code[j],
						v_site_id,
						now(),
						now(),
						'NO_DATA',
						now(),
						2,
						2
					);

			END IF;

			-- temporary get cabinet_id after create or update
			SELECT cb.id INTO tmp_cabinet_id
		    FROM smartsite.cabinet cb
		    WHERE "code" = v_cabinet_code[j] and "site_id" = v_site_id
		    ;

		 -- ## 3 CHECK DEVICE CONFIGURATION ## --
			--v_cabinet_1 := string_to_array(p_cabinet_1, '|');
			v_cabinet_device := NULL;
			v_device_info := NULL;
			v_cabinet_device := string_to_array(v_cabinet[j], '|');
			RAISE INFO 'v_cabinet[j]: %',v_cabinet[j];
			--FOR i IN array_lower(v_cabinet_1, 1) .. array_upper(v_cabinet_1, 1) LOOP
			--    v_device_info := string_to_array(v_cabinet_1[i], ',');
			FOR i IN array_lower(v_cabinet_device, 1) .. array_upper(v_cabinet_device, 1) LOOP
				v_device_info := string_to_array(v_cabinet_device[i], ',');
				RAISE INFO 'i: %',i;
				RAISE INFO 'v_device_info[1]: %',v_device_info[1];
				RAISE INFO 'v_device_info[2]: %',v_device_info[2];
				RAISE INFO 'v_device_info[3]: %',v_device_info[3];
				IF v_device_info[1] is NULL THEN
						RAISE EXCEPTION 'device_type_code is NULL.';
				ELSEIF v_device_info[2] is NULL THEN
						RAISE EXCEPTION 'is_installed is NULL.';
				ELSEIF v_device_info[3] is NULL THEN
						RAISE EXCEPTION 'is_enabled is NULL.';
				END IF;


				v_count_device_enable = v_count_device_enable+ (CASE v_device_info[3] WHEN 't' THEN 1 ELSE 0 END);

				SELECT COUNT(1) INTO v_count_device
				FROM smartsite.device d
				WHERE d.cabinet_id = tmp_cabinet_id AND d.device_type_code = CAST (v_device_info[1] AS INTEGER);

				IF v_count_device > 0 THEN
					-- ## UPDATE DEVICE
					RAISE INFO 'UPDATE DEVICE: %',v_device_info[1];
					UPDATE smartsite.device
					SET "active" = 't',
						"is_install" = CASE v_device_info[2] WHEN 't' THEN TRUE ELSE FALSE END,
						"is_enable" = CASE v_device_info[3] WHEN 't' THEN TRUE ELSE FALSE END
						--"serialnum" = NULL,
						--"status_dt" = NULL,
						--"created_dt" = now(),
						--"installed_dt" = NULL
					WHERE cabinet_id = tmp_cabinet_id AND device_type_code = CAST (v_device_info[1] AS INTEGER);

				ELSE
					-- ## ADD NEW DEVICE
					RAISE INFO 'ADD NEW DEVICE: %',v_device_info[1];
					INSERT INTO smartsite.device (
						"active",
						"is_install",
						"is_enable",
						"serialnum",
						"device_type_code",
						"cabinet_id",
						"status",
						"created_at",
						"updated_at",
						"installed_date"
					)
					VALUES
						(
							't',
							CASE v_device_info[2] WHEN 't' THEN TRUE ELSE FALSE END,
							CASE v_device_info[3] WHEN 't' THEN TRUE ELSE FALSE END,
							NULL,
							CAST (v_device_info[1] AS INTEGER),
							tmp_cabinet_id,
							2, --Unknow status
							now(),
							now(),
							NULL
						);
				END IF;

			END LOOP;

		END IF; -- END CABINET CONFIGURATION1
		--ELSE

		RAISE INFO 'v_count_device_enable: %',v_count_device_enable;
        IF v_count_device_enable = 0  THEN
        	RAISE INFO 'v_count_device_enable: =0';
			--IF v_count_cabinet > 0 THEN
			-- ## INACTIVE CABINET1
			RAISE INFO 'INACTIVE CABINET: %',v_cabinet_code[j];
			UPDATE smartsite.cabinet
			SET "active" = 'f',
				"updated_at" = now()
			WHERE "code" = v_cabinet_code[j] and "site_id" = v_site_id;
            v_count_cabinet_enable = v_count_cabinet_enable - 1;
		END IF; 

	END LOOP;

	IF v_count_cabinet_enable = 0  THEN
        RAISE INFO 'v_count_cabinet_enable: =0';
        RAISE INFO 'INACTIVE SITE: %',p_site_code;
        UPDATE smartsite.site
        SET "active" = 'f',
            "updated_at" = now()
        WHERE "code" = p_site_code;
    END IF;
	
END;
$BODY$;