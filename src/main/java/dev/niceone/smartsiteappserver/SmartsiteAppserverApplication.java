package dev.niceone.smartsiteappserver;

import dev.niceone.smartsiteappserver.model.*;
import dev.niceone.smartsiteappserver.model.constant.*;
import dev.niceone.smartsiteappserver.repository.*;
import dev.niceone.smartsiteappserver.scheduler.SiteScheduler;
import lombok.extern.log4j.Log4j2;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.LocalDateTime;
import java.util.*;

//import org.springframework.cloud.netflix.eureka.EnableEurekaClient;

@SpringBootApplication
@EnableCaching
@EnableAsync
@EnableJpaAuditing
@EnableScheduling
@EntityScan("dev.niceone.smartsiteappserver.model")
@EnableJpaRepositories("dev.niceone.smartsiteappserver.repository")
@EnableElasticsearchRepositories("dev.niceone.smartsiteappserver.elastic.repository")
@Log4j2
public class SmartsiteAppserverApplication {

    @Autowired
    private Environment env;

    /**
     * Defaults applied under every deployment (WAR on WildFly via ServletInitializer, or main()).
     * They have the lowest precedence, so the per-node external properties can still override them.
     *
     * open-in-view=false: with OSIV each HTTP request held its JDBC connection until the response
     * was written (seen in prod as "Apparent connection leak" from ioReport with pool size 5).
     * enable_lazy_load_no_trans keeps existing lazy associations serialisable outside a transaction
     * (each lazy load borrows a connection only for that one query).
     */
    static Map<String, Object> defaultProperties() {
        Map<String, Object> props = new HashMap<>();
        props.put("spring.jpa.open-in-view", "false");
        props.put("spring.jpa.properties.hibernate.enable_lazy_load_no_trans", "true");
        return props;
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(SmartsiteAppserverApplication.class);
        app.setDefaultProperties(defaultProperties());
        app.run(args);
    }

//    @Bean
//    public SiteScheduler task() {
//        String site_scheduler = env.getProperty("app.site_scheduler");
//        if (site_scheduler.equals("yes")) {
//            return new SiteScheduler();
//        }
//        return null;
//    }

//    @Bean
//    WebMvcConfigurer configurer(){
//        return new WebMvcConfigurerAdapter(){
//            @Override
//            public void configureAsyncSupport (AsyncSupportConfigurer configurer) {
//                ThreadPoolTaskExecutor t = new ThreadPoolTaskExecutor();
//                t.setCorePoolSize(10);
//                t.setMaxPoolSize(100);
//                t.setQueueCapacity(50);
//                t.setAllowCoreThreadTimeOut(true);
//                t.setKeepAliveSeconds(120);
//                t.initialize();
//                configurer.setTaskExecutor(t);
//            }
//        };
//    }

//    @Profile("dev")
//    @Bean
//    CommandLineRunner initLogDatabase(CommonLogRepository commonLogRepository) {
//        return args -> {
//            System.out.println("init logdb data...");
//            log.debug("init logdb data...");
//
//            CommonLog logInit = new CommonLog("start server");
//            commonLogRepository.save(logInit);
//        };
//    }

    @Profile("dev")
    @Bean
    CommandLineRunner initDatabase(AlarmLogRepository alarmLogRepository,
                                   RegionRepository regionRepo,
                                   ProvinceRepository provinceRepo,
                                   DistrictRepository districtRepository,
                                   SubDistrictRepository subDistrictRepository,
                                   RoleRepository roleRepository,
                                   SiteRepository siteRepository,
                                   UserRepository userRepository) {

        return args -> {
            System.out.println("System.out init dev data...");
            log.debug("debug init dev data...");
            log.info("info init dev data...");
            log.warn("warn init dev data...");

            log.trace("This is a trace message");
            log.debug("This is a debug message");
            log.info("This is an info message");
            log.warn("This is a warning message");
            log.error("This is an error message");
            log.fatal("This is a fatal message");

            // Check if data already exists to prevent duplicate key errors
            long userCount = userRepository.count();
            if (userCount > 0) {
                log.info("Development data already exists. Skipping initialization.");
                return;
            }

            Region region1 = regionRepo.findByCode("BKK");
//            Region region2 = regionRepo.findByCode("CR");
//
            Province province1 = provinceRepo.findByCode("BKK");
//            Province province2 = provinceRepo.findByCode("CMI");

            District district1 = districtRepository.findByProvinceId(province1.getId()).get(0);
            SubDistrict subDistrict1 = subDistrictRepository.findByDistrictId(district1.getId()).get(0);

//            District district1 = new District(province1, 10000, "อำเภอกรุงเทพ1", "Dis Bangkok1", true);
//            District district2 = new District(province1, 20000, "อำเภอกรุงเทพ2", "Dis Bangkok2", true);
//            District district3 = new District(province1, 30000, "อำเภอกรุงเทพ3", "Dis Bangkok3", true);
//            District district4 = new District(province2, 40000, "อำเภอเชียงใหม่1", "Dis ChiangMai1", true);
//            District district5 = new District(province2, 50000, "อำเภอเชียงใหม่2", "Dis ChiangMai2", true);
//            districtRepository.save(district1);
//            districtRepository.save(district2);
//            districtRepository.save(district3);
//            districtRepository.save(district4);
//            districtRepository.save(district5);

//            SubDistrict emptyDistrict = new SubDistrict(district1, "ว่าง", "empty", true);
//            subDistrictRepository.save(emptyDistrict);
//
//            SubDistrict subDistrict1 = subDistrictRepository.findById((long) 120610).orElse(emptyDistrict);;
//            SubDistrict subDistrict4 = subDistrictRepository.findById((long) 101401).orElse(emptyDistrict);

//            Role role1 = new Role(RoleName.ROLE_USER);
//            Role role2 = new Role(RoleName.ROLE_ADMIN);
//            roleRepository.save(role1);
//            roleRepository.save(role2);
//            Role userRole = roleRepository.findByName(RoleName.ROLE_USER)
//                        .orElseThrow(() -> new AppException("User Role not set."));
//            Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN)
//                    .orElseThrow(() -> new AppException("User Role not set."));

            Set<Role> roleSet = new HashSet<>();
            Set<Role> roleSuperAdmin = new HashSet<>();
            Role role1 = roleRepository.findByName(RoleName.ROLE_ADMIN).orElseThrow(null);
            Role role2 = roleRepository.findByName(RoleName.ROLE_USER).orElseThrow(null);
            Role role3 = roleRepository.findByName(RoleName.ROLE_SUPER_ADMIN).orElseThrow(null);

            roleSet.add(role1);
            roleSet.add(role2);

            roleSuperAdmin.add(role1);
            roleSuperAdmin.add(role2);
            roleSuperAdmin.add(role3);

            User user1 = new User("admin", "secret", "admin", "admin");
            user1.setUsername("admin");
            user1.setRoles(roleSuperAdmin);
            user1.setRegions(Collections.singleton(region1));
            userRepository.save(user1);

            User user2 = new User("staff", "secret", "staff", "staff");
            user2.setUsername("staff");
            user2.setRoles(roleSet);
            user2.setRegions(Collections.singleton(region1));
            userRepository.save(user2);

//            user2.setRoles(Collections.singleton(role1));
//            userRepository.save(user2);
//            String code, String name_th,
//                    Region region, Province province,
//                    District district, SubDistrict subDistrict

            // Site
            Site site1 = new Site("TLCEM", "บ้านคลองข่อย", region1, province1, district1,
                    subDistrict1, LocalDateTime.now(), "15.5964144", "99.5818812", "192.168.0.101",
                    8080,"255.255.255.0", "192.168.0.1", "address 1",
                    StatusCodeEnum.STATUS_UNKNOWN, true);
            site1 = createSiteData(site1);
            siteRepository.save(site1);

            // Site 2
            Site site2 = new Site("AACBP", "พหลเซ็นเตอร์2", region1, province1, district1,
                    null, null, "", "", "192.168.0.102", 8080,
                    "", "", "", StatusCodeEnum.STATUS_UP, true);
            site2 = createSiteData(site2);
            site2.setReportStatusAt(LocalDateTime.now());
            siteRepository.save(site2);

            Site site3 = new Site("TEST", "ทดสอบ", region1, province1, district1,
                    subDistrict1, LocalDateTime.now(), "25.59", "100.58", "localhost",
                    1234,"", "", "address 1234",
                    StatusCodeEnum.STATUS_DOWN, false);
            site3 = createSiteData(site3);
            siteRepository.save(site3);

            // AlarmLog
            List<AlarmLog> createAlarmLogList1 = createAlarmLog(site1);
            alarmLogRepository.saveAll(createAlarmLogList1);
//
//            List<AlarmLog> createAlarmLogList3 = createAlarmLog(site3);
//            alarmLogRepository.saveAll(createAlarmLogList3);
        };
    }

    public Site createSiteData (Site site) {
        for (int cabinetNo = 1; cabinetNo < 4; cabinetNo++) {
            LockEventCodeEnum lockEvent = LockEventCodeEnum.LOCK_EVENT_UNKNOW;
            DoorEventCodeEnum doorEvent = DoorEventCodeEnum.DOOR_EVENT_UNKNOW;
            StatusCodeEnum statusCode = StatusCodeEnum.STATUS_UNKNOWN;
            boolean active = false;
            if (cabinetNo == 1){
                lockEvent = LockEventCodeEnum.LOCK_EVENT_UNLOCK;
                doorEvent = DoorEventCodeEnum.DOOR_EVENT_OPEN;
                statusCode = StatusCodeEnum.STATUS_UP;
                active = true;
            }
            else if (cabinetNo == 2){
                lockEvent = LockEventCodeEnum.LOCK_EVENT_LOCK;
                doorEvent = DoorEventCodeEnum.DOOR_EVENT_CLOSE;
                statusCode = StatusCodeEnum.STATUS_DOWN;
                active = true;
            }
            Cabinet tmpCabinet = new Cabinet(site.getCode() +"-" + cabinetNo, cabinetNo,
                    active, lockEvent, doorEvent,"");
            StatusCodeEnum finalStatusCode = statusCode;
            int finalCabinetNo = cabinetNo;
            Arrays.stream(DeviceTypeCodeEnum.values()).forEach(deviceType -> {
                Device tmpDevice = new Device(deviceType, finalCabinetNo %2==0,
                        finalCabinetNo %2!=0, finalStatusCode);
                tmpCabinet.addDevice(tmpDevice);
            });


//            if (tmpCabinet.getCabinetNo() == 1) {
//                tmpCabinet.addAuxDevice(new AuxDevice(0, false));
//                tmpCabinet.addAuxDevice(new AuxDevice(1, true));
//                tmpCabinet.addAuxDevice(new AuxDevice(2, true));
//                tmpCabinet.addAuxDevice(new AuxDevice(3, true));
//            } else {
//                tmpCabinet.addAuxDevice(new AuxDevice(0, false));
//            }
            site.addCabinet(tmpCabinet);
        }
        return site;
    }

    public List<AlarmLog> createAlarmLog (Site site) {
        List<AlarmLog> createAlarmLogList = new ArrayList<>();
        // Alarm Log
        createAlarmLogList.add(
                new AlarmLog(AlarmTypeCodeEnum.ALARM_TYPE_BUZZER_FAILED, site, null, site.getCabinets().get(0).getDevices().get(2),
                        LocalDateTime.now().minusHours(2)));
        createAlarmLogList.add(
                new AlarmLog(AlarmTypeCodeEnum.ALARM_TYPE_CCTV_FAILED, site, null, site.getCabinets().get(0).getDevices().get(4),
                        LocalDateTime.now()));
        createAlarmLogList.add(
                new AlarmLog(AlarmTypeCodeEnum.ALARM_TYPE_MOTION_FAILED, site, null, site.getCabinets().get(1).getDevices().get(5),
                        LocalDateTime.now().minusDays(1)));
        createAlarmLogList.add(
                new AlarmLog(AlarmTypeCodeEnum.ALARM_TYPE_VIBRAT_FAILED, site, null, site.getCabinets().get(2).getDevices().get(6),
                        null));
        createAlarmLogList.add(
                new AlarmLog(AlarmTypeCodeEnum.ALARM_TYPE_LOCK_ATTEMPT_FAILED, site, site.getCabinets().get(0), null,  LocalDateTime.now().minusHours(2)));
        createAlarmLogList.add(
                new AlarmLog(AlarmTypeCodeEnum.ALARM_TYPE_MOTION_EXCESS, site, site.getCabinets().get(1), null, LocalDateTime.now()));
        createAlarmLogList.add(
                new AlarmLog(AlarmTypeCodeEnum.ALARM_TYPE_SLAVE_DOWN, site, site.getCabinets().get(1), null,  LocalDateTime.now().minusDays(1)));
        createAlarmLogList.add(
                new AlarmLog(AlarmTypeCodeEnum.ALARM_TYPE_UNAUTH_OPENED, site, site.getCabinets().get(0), null, null));
        createAlarmLogList.add(
                new AlarmLog(AlarmTypeCodeEnum.ALARM_TYPE_COMM_LOST_PI, site, null, null, LocalDateTime.now().minusHours(2)));
        createAlarmLogList.add(
                new AlarmLog(AlarmTypeCodeEnum.ALARM_TYPE_COMM_LOST_SERV, site, null, null, LocalDateTime.now()));
        createAlarmLogList.add(
                new AlarmLog(AlarmTypeCodeEnum.ALARM_TYPE_COMM_LOST_SERV, site, null, null,  LocalDateTime.now().minusDays(1)));
        createAlarmLogList.add(
                new AlarmLog(AlarmTypeCodeEnum.ALARM_TYPE_COMM_LOST_SERV, site, null, null, null));
        return createAlarmLogList;
    }
}
