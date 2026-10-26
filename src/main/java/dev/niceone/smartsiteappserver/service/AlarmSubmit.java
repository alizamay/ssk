package dev.niceone.smartsiteappserver.service;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import dev.niceone.smartsiteappserver.model.AlarmLog;
import dev.niceone.smartsiteappserver.model.LogDataModel;
import dev.niceone.smartsiteappserver.model.LogEntry;
import dev.niceone.smartsiteappserver.model.Site;
import dev.niceone.smartsiteappserver.model.constant.AlarmTypeCodeEnum;
import dev.niceone.smartsiteappserver.repository.AlarmLogRepository;
import dev.niceone.smartsiteappserver.repository.ProvinceRepository;
import dev.niceone.smartsiteappserver.repository.SiteRepository;
import lombok.extern.java.Log;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@EnableAsync
@Slf4j
@Service
public class AlarmSubmit {
    @Value("${app.alarm_ip}")
    private String alarm_ip;

    @Value("${app.alarm_port}")
    private String alarm_port;

    @Value("${app.alarm_filename}")
    private String alarm_filename;

    @Value("${app.alarm_file_path}")
    private String alarm_file_path;

    // TODO: change me
    private String appserv_ip = "localhost";

    @Autowired
    private AlarmLogRepository alarmLogRepository;

    @Autowired
    private SiteRepository siteRepository;
    @Autowired
    private ProvinceRepository provinceRepository;

    public void writeAlarmFile(JSONArray alarmList) throws JsonParseException, JsonMappingException, IOException, JSONException {

        if(alarmList.length() == 0) return;

        Date date = new Date(System.currentTimeMillis());
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String dateTime = dateFormat.format(date);

        //Format Date time
        String yyyy = dateTime.substring(0,4);
        String mm = dateTime.substring(5,7);
        String dd = dateTime.substring(8,10);

        String hh = dateTime.substring(11,13);
        String min = dateTime.substring(14,16);
        String ss = dateTime.substring(17);

        String filename = alarm_filename + "_" + yyyy + mm + dd + "_" + hh + min + ss + ".txt";

        String sRootPath = alarm_file_path;
        java.nio.file.Path destinationFile = Paths.get(sRootPath + "/", filename);

        // SMART_SITE|SMART_SITE|AppServerDev|10.138.36.77|E131M|172.16.34.46|Phahon Yothin Center|Smart Lock Failed|major|Smart Lock Failed|2019-06-13 21:21:21
        // SMART_SITE|SMART_SITE|AppServerDev|10.138.36.77|E131M|172.16.34.46|Phahon Yothin Center|Smart Lock Failed|clear|Smart Lock Failed|2019-06-13 21:22:22

        List<String> lines = new ArrayList<String>();
        // List<String> lines = Arrays.asList("The first line", "The second line");

        for (int i = 0; i < alarmList.length(); i++) {

            String line = "SMART_SITE|SMART_SITE|AppServer" + "|" + appserv_ip;

            JSONObject item = alarmList.getJSONObject(i);
            int alarm_type_id = item.getInt("alarm_type_id");
            String site_code = item.getString("site_code");
            int alarm_to_clear = item.getInt("alarm_to_clear");
            String status_dt = item.getString("status_dt");

            //TODO
            String site_name = "site_nameeeeeeeeeeeeeee";
            String ipaddr = "ipaddrrrrr";
            String alarm_type = "alarm_typeeee";
            String severity = "severityyyy";

            if(alarm_to_clear == 1) severity = "clear";

            line += "|" + site_code + "|" + ipaddr + "|" + site_name + "|" + alarm_type + "|" + severity + "|" + alarm_type + "|" + status_dt;

            lines.add(line);
        }

        try {

            String cmd = alarm_file_path + "/sendalarm " + alarm_ip + " " + alarm_port + " " + destinationFile;

            Files.write(destinationFile, lines, StandardCharsets.UTF_8);

            Process p;
            String msg = cmd;

            p = Runtime.getRuntime().exec(cmd);

            p.waitFor();
            p.destroy();

//            ActivityLog.saveLog("submitAlarmCFMS", "Send to CFMS", msg, "Success");

        } catch (Exception e) {
            String msg = e.getMessage();
//            ActivityLog.saveLog("submitAlarmCFMS", "Send to CFMS", msg, "Error");
        }
    }

    public void writeAlarmFile2(List<AlarmLog> alarmList) throws IOException {
//        log.info("Start writeAlarmFile2 !!");
        if(alarmList.isEmpty()) return;

        LocalDateTime localDateTime = LocalDateTime.now();
        String yyyy = String.format("%04d", localDateTime.getYear());
        String mm = String.format("%02d", localDateTime.getMonthValue());
        String dd = String.format("%02d", localDateTime.getDayOfMonth());
        String HH = String.format("%02d", localDateTime.getHour());
        String MM = String.format("%02d", localDateTime.getMinute());
        String SS = String.format("%02d", localDateTime.getSecond());
        String filename = alarm_filename + "_" + yyyy + mm + dd + "_" + HH + MM + SS + ".txt";
        log.info("filename: " + filename);

        String sRootPath = alarm_file_path;

        BufferedWriter writer = new BufferedWriter(new FileWriter(sRootPath+"/"+filename));

        // SMART_SITE|SMART_SITE|AppServerDev|10.138.36.77|E131M|172.16.34.46|Phahon Yothin Center|Smart Lock Failed|major|Smart Lock Failed|2019-06-13 21:21:21
        // SMART_SITE|SMART_SITE|AppServerDev|10.138.36.77|E131M|172.16.34.46|Phahon Yothin Center|Smart Lock Failed|clear|Smart Lock Failed|2019-06-13 21:22:22

        for (AlarmLog alarm : alarmList) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String formatDateTime = alarm.getUpdatedAt().format(formatter);
            String severity = alarm.getAlarmType().severity;
            if(alarm.getResolveDate() != null) {
                severity = "clear";
                formatDateTime = alarm.getResolveDate().format(formatter);
            }

            writer.write("SMART_SITE|" +
                    "SMART_SITE|" +
                    "AppServer|" +
                    appserv_ip + "|" +
                    alarm.getSite().getCode() + "|" +
                    alarm.getSite().getIp_address() + "|" +
                    alarm.getSite().getName_th() + "|" +
                    alarm.getAlarmType().name + "|" +
                    severity + "|" +
                    alarm.getAlarmType().name + "|" +
                    formatDateTime);
            writer.newLine();
            alarm.setWriteToFile(true);
        }
        writer.close();
        alarmLogRepository.saveAll(alarmList);
        log.info("close filename: " + filename);
    }


    @Async
    public void writeAlarmFile3(List<LogDataModel> alarmList) throws IOException {
//        log.info("Start writeAlarmFile2 !!");
        if(alarmList.isEmpty()) return;

        LocalDateTime localDateTime = LocalDateTime.now();
        String yyyy = String.format("%04d", localDateTime.getYear());
        String mm = String.format("%02d", localDateTime.getMonthValue());
        String dd = String.format("%02d", localDateTime.getDayOfMonth());
        String HH = String.format("%02d", localDateTime.getHour());
        String MM = String.format("%02d", localDateTime.getMinute());
        String SS = String.format("%02d", localDateTime.getSecond());
        String filename = alarm_filename + "_" + yyyy + mm + dd + "_" + HH + MM + SS + ".txt";
        log.info("filename: " + filename);

        String sRootPath = alarm_file_path;

        try{
            BufferedWriter writer = new BufferedWriter(new FileWriter(sRootPath+"/"+filename));

            // SMART_SITE|SMART_SITE|AppServerDev|10.138.36.77|E131M|172.16.34.46|Phahon Yothin Center|Smart Lock Failed|major|Smart Lock Failed|2019-06-13 21:21:21
            // SMART_SITE|SMART_SITE|AppServerDev|10.138.36.77|E131M|172.16.34.46|Phahon Yothin Center|Smart Lock Failed|clear|Smart Lock Failed|2019-06-13 21:22:22

            for (LogDataModel alarm : alarmList) {

                log.info("writeAlarmFile3",alarm);

                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                String formatDateTime = alarm.getUpdate_date().format(formatter);
                String severity = AlarmTypeCodeEnum.getById(alarm.getAlarm_type()).severity;
                if(alarm.getResolve_date() != null) {
                    severity = "clear";
                    formatDateTime = alarm.getResolve_date().format(formatter);
                }

                Site site = siteRepository.findByCode(alarm.getSite_code()).get();


                writer.write("SMARTSITE|" +
                        appserv_ip + "|" +
                        "SMARTSITE|" +
                        site.getCode() + "|" +
                        site.getIp_address() + "|" +
                        AlarmTypeCodeEnum.getById(alarm.getAlarm_type()).aKey + "|" +
                        AlarmTypeCodeEnum.getById(alarm.getAlarm_type()).aName + "|" +
                        "SmartSite Master device " + site.getCode() + "|" +
                        severity + "|" +
                        AlarmTypeCodeEnum.getById(alarm.getAlarm_type()).name + "|" +
                        formatDateTime + "|" +
                        site.getProvince().getCode());
//            writer.write("SMART_SITE|" +
//                    "SMART_SITE|" +
//                    "AppServer|" +
//                    appserv_ip + "|" +
//                    site.getCode() + "|" +
//                    site.getIp_address() + "|" +
//                    site.getName_th() + "|" +
//                    AlarmTypeCodeEnum.getById(alarm.getAlarm_type()).name + "|" +
//                    severity + "|" +
//                    AlarmTypeCodeEnum.getById(alarm.getAlarm_type()).name + "|" +
//                    formatDateTime);
                writer.newLine();
                //alarm.setWriteToFile(true);
            }
            writer.close();
        }catch (Exception ex){

        }





        //alarmLogRepository.saveAll(alarmList);
        log.info("close filename: " + filename);
    }
}
