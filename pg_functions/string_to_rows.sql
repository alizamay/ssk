CREATE OR REPLACE FUNCTION public.string_to_rows(IN text)
    RETURNS SETOF text
    LANGUAGE 'plpgsql' VOLATILE
    COST 100    ROWS 1000
AS $BODY$ 
  DECLARE
    elems text[];      
  BEGIN
    elems := string_to_array($1, ',');
    FOR i IN array_lower(elems, 1) .. array_upper(elems, 1) LOOP
      RETURN NEXT elems[i];
    END LOOP;
    RETURN;
  END
$BODY$;