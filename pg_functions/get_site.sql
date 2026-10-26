CREATE OR REPLACE FUNCTION public.get_site(IN p_site character varying)
    RETURNS TABLE(site_name character varying)
    LANGUAGE 'plpgsql'
    VOLATILE
    COST 100    ROWS 1000
AS $BODY$
BEGIN
	--Routine body goes here...

	RETURN QUERY select name_th from public.site where code = p_site;
END
$BODY$;