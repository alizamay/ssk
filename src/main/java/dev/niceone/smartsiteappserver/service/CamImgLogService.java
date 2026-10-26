package dev.niceone.smartsiteappserver.service;

import dev.niceone.smartsiteappserver.mapper.AlarmLogModelMapper;
import dev.niceone.smartsiteappserver.model.AlarmLog;
import dev.niceone.smartsiteappserver.model.Cabinet;
import dev.niceone.smartsiteappserver.model.CamImgLog;
import dev.niceone.smartsiteappserver.model.Site;
import dev.niceone.smartsiteappserver.payload.AlarmLogResponse;
import dev.niceone.smartsiteappserver.payload.ApiResponse;
import dev.niceone.smartsiteappserver.payload.response.CamImgLogResponse;
import dev.niceone.smartsiteappserver.repository.AlarmLogRepository;
import dev.niceone.smartsiteappserver.repository.CabinetRepository;
import dev.niceone.smartsiteappserver.repository.CamImgLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class CamImgLogService {
    private AlarmLogRepository alarmLogRepository;
    private CamImgLogRepository camImgLogRepository;
    private CabinetRepository cabinetRepository;
    @Autowired
    private Environment env;

    @Autowired
    public CamImgLogService(AlarmLogRepository alarmLogRepository, CamImgLogRepository camImgLogRepository, CabinetRepository cabinetRepository) {
        this.alarmLogRepository = alarmLogRepository;
        this.camImgLogRepository = camImgLogRepository;
        this.cabinetRepository = cabinetRepository;
    }

    // Create
    public ResponseEntity<ApiResponse> createCamImgLog(Site site, Cabinet cabinet,
                                                       String img_path, String img_filename,
                                                       AlarmLog alarmLog) {
        CamImgLog camImgLog = new CamImgLog();

        camImgLog.setActive(true);
        camImgLog.setSite(site);
        camImgLog.setCabinet(cabinet);
        camImgLog.setImg_path(img_path);
        camImgLog.setImg_filename(img_filename);
        if (alarmLog != null) {
            camImgLog.setAlarmLog(alarmLog);
        }
        camImgLogRepository.save(camImgLog);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest().path("/{id}")
                .buildAndExpand(camImgLog.getId()).toUri();
        return ResponseEntity.created(location)
                .body(new ApiResponse(true, "CamImgLog Created Successfully"));
    }

    public List<CamImgLogResponse> getList(Specification<CamImgLog> spec,String start_date,String end_date,String cabinet_id) {
        //List<CamImgLog> camImgLogList = camImgLogRepository.findAll(spec);
        List<CamImgLogResponse> responses = new ArrayList<>();

        String img_snapshot_path = env.getProperty("app.img_snapshot_path");
        Long Id = Long.valueOf(cabinet_id);
        Cabinet cabinet = cabinetRepository.findById(Id).orElse(null);

        LocalDateTime startDate = LocalDateTime.parse(start_date).plusHours(7);
        LocalDateTime endDate = LocalDateTime.parse(end_date).plusHours(7);

        LocalDateTime checkMaxDate = LocalDateTime.parse(end_date);

        LocalDate startDateOnly = startDate.toLocalDate();
        LocalDate endDateOnly = endDate.toLocalDate();

        if(startDateOnly.isEqual(endDateOnly)){
            checkMaxDate = checkMaxDate.plusDays(1);
        }
        if(cabinet != null){

            //log.info("get image cabinet " + cabinet.getCode());
            //log.info("get image start_date " + start_date);
            //log.info("get image end_date " + end_date);

            String input_cabinetCode = cabinet.getCode();
            for (LocalDateTime date = startDate; date.isBefore(checkMaxDate); date = date.plusDays(1))
            {
                String yyyy = String.format("%04d", date.getYear());
                String mm = String.format("%02d", date.getMonthValue());
                String dd = String.format("%02d", date.getDayOfMonth());

                String sRootPath = "";
                sRootPath = img_snapshot_path;
                String newPath = "/" + input_cabinetCode + "/" + yyyy +"/"+ mm +"/"+ dd + "/" ;

                String fullpath = sRootPath+newPath;
                //log.info("get image " + fullpath);
                File folder = new File(fullpath);
                File[] listOfFiles = folder.listFiles();
                if(listOfFiles != null) {
                    for (int i = 0; i < listOfFiles.length; i++) {
                        if (listOfFiles[i].isFile()) {
                            Path filePath = Paths.get(fullpath + "/" + listOfFiles[i].getName());

                            LocalDateTime cDate = date;

                            try {
                                BasicFileAttributes attr = Files.readAttributes(filePath, BasicFileAttributes.class);
                                LocalDateTime localDateTime = attr.creationTime().toInstant()
                                        .atZone(ZoneId.systemDefault())
                                        .toLocalDateTime();
                                cDate = localDateTime;
                            }catch (IOException ex){

                            }
                            log.info("getList CAM Img Log Create Date : " + cDate);
                            log.info("getList CAM Img Log startDate  : " + startDate);
                            log.info("getList CAM Img Log endDate  : " + endDate);
                            log.info("getList CAM Img Log Result Condition  : " + (cDate.isAfter(startDate) && cDate.isBefore(endDate)));
                            if (cDate.isAfter(startDate) && cDate.isBefore(endDate)) {
                                System.out.println("File " + listOfFiles[i].getName());
                                CamImgLogResponse _response = new CamImgLogResponse();
                                _response.setId((long)i);
                                _response.setCreatedAt(cDate);
                                _response.setFileName(listOfFiles[i].getName());
                                _response.setUrl("/cam-img-log/img"+newPath+listOfFiles[i].getName());
                                responses.add(_response);
                            }
                        }
                    }
                }
            }
        }


//        for (CamImgLog camImgLog : camImgLogList) {
//            CamImgLogResponse _response = new CamImgLogResponse();
//            _response.setId(camImgLog.getId());
//            _response.setCreatedAt(camImgLog.getCreatedAt());
//            _response.setFileName(camImgLog.getImg_filename());
//            _response.setUrl("/cam-img-log/img"+camImgLog.getImg_path()+camImgLog.getImg_filename());
//            responses.add(_response);
//        }
        return responses;
    }
}
