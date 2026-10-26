CREATE OR REPLACE FUNCTION public.check_ip_address_available(IN s_code character varying, IN s_ip character varying)
    RETURNS TABLE(result_code character varying, result_desc character varying)
    LANGUAGE 'plpgsql' VOLATILE
    COST 100    ROWS 1000
AS $BODY$
declare
	v_RESULT_CODE VARCHAR(1);
	v_RESULT_DESC VARCHAR(255);

	BEGIN
	
		IF EXISTS (select 1 from public.site s where s.code = s_code and s.ip_address = s_ip) THEN
			v_RESULT_CODE := '0';
			v_RESULT_DESC := 'SUCCESS';
		ELSE
			IF EXISTS (select 1 from public.site si where si.code <> s_code and si.ip_address = s_ip) then
				v_RESULT_CODE := '1';
				v_RESULT_DESC := 'IP address is already in use, Please contact admin.';
			ELSE
				v_RESULT_CODE := '0';
				v_RESULT_DESC := 'SUCCESS';
			END IF;
		END IF;
		
		RETURN QUERY select v_RESULT_CODE as result_code, v_RESULT_DESC as result_desc;
	
	END;
$BODY$;