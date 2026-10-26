--add default constrain to column id in public.site === nextval('public.site_id_seq'::regclass)
--add default constrain to column id in public.cabinet === nextval('public.cabinet_id_seq'::regclass)
--add default constrain to column id in public.device === nextval('public.device_id_seq'::regclass)

SELECT smartsite.save_smart_site_config(
               'BKKYM',
               'BAN KHLONGKOY',
               'BKK',
               '13.9389',
               '100.456',
               '10.242.16.164',
               8080,
               'โฉนดที่ดินเลขที่ 2204 เลขที่ดิน 31 หน้าสำรวจ 423',
               '255.255.255.240',
               '10.242.16.161',
               'PR157',
               'AP1745',
               'TB120611',
               '1,t,t|2,t,t|3,t,t|4,t,t|5,t,t|6,t,t|7,t,t|8,t,t|9,t,t|10,t,t',
               '1,t,t|2,t,t|3,t,t|4,t,t|5,t,t|6,t,t|7,t,t|8,t,t|9,t,t|10,t,t',
               '1,f,f|2,f,f|3,f,f|4,f,f|5,f,f|6,f,f|7,f,f|8,f,f|9,f,f|10,f,f'
           )
SELECT public.get_smart_site_info('100CP')