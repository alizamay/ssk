package dev.niceone.smartsiteappserver.controller;

import dev.niceone.smartsiteappserver.model.CamImgLog;
import dev.niceone.smartsiteappserver.payload.response.CamImgLogResponse;
import dev.niceone.smartsiteappserver.service.CamImgLogService;
import lombok.extern.slf4j.Slf4j;
import net.kaczmarzyk.spring.data.jpa.domain.Equal;
import net.kaczmarzyk.spring.data.jpa.domain.GreaterThanOrEqual;
import net.kaczmarzyk.spring.data.jpa.domain.LessThanOrEqual;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Spec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import javax.servlet.ServletContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

@Spec(path="createdAt", params = "start_date", spec= GreaterThanOrEqual.class)
interface StartDateSpec extends Specification<CamImgLog> {
}

@Spec(path="createdAt", params = "end_date", spec= LessThanOrEqual.class)
interface EndDateSpec extends Specification<CamImgLog> {
}

@Spec(path="cabinet.id", params = "cabinet_id", spec= Equal.class)
interface CabinetSpec extends Specification<CamImgLog> {
}

@RestController
@Slf4j
@RequestMapping("/api/cam-img-log")
public class CamImgLogController {
    @Autowired
    private CamImgLogService camImgLogService;

    @Autowired
    private ServletContext servletContext;

    @Autowired
    private Environment env;

    // Get
    @GetMapping("/list")
    List<CamImgLogResponse> findCamImgLog(
            @RequestParam(name="start_date", required=false) String start_date,
            @RequestParam(name="end_date", required=false) String end_date,
            @RequestParam(name="cabinet_id", required=false) String cabinet_id,
            StartDateSpec startCreatedDateSpec,
            EndDateSpec endCreatedDateSpec,
            CabinetSpec cacbinetSpec) {
        //log.info("findCamImgLog");
        return camImgLogService.getList(
                cacbinetSpec.and(startCreatedDateSpec).and(endCreatedDateSpec),
                start_date,end_date,cabinet_id
        );
    }

    // View Image
    @GetMapping("/img/{cabinet}/{yy}/{mm}/{dd}/{filename}")
    public ResponseEntity<?> getImageAsResponseEntity(@PathVariable String cabinet,
                                                      @PathVariable String yy,
                                                      @PathVariable String mm,
                                                      @PathVariable String dd,
                                                      @PathVariable String filename) {
        String img_snapshot_path = env.getProperty("app.img_snapshot_path");
        //log.info(img_snapshot_path+"/"+cabinet+"/"+yy+"/"+mm+"/"+dd+"/"+filename);
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl(CacheControl.noCache().getHeaderValue());
        headers.setContentType(MediaType.IMAGE_JPEG);
        java.nio.file.Path destinationFile = Paths.get(img_snapshot_path+"/"+cabinet+"/"+yy+"/"+mm+"/"+dd+"/"+filename);

        byte[] media = new byte[0];
        try {
            media = Files.readAllBytes(destinationFile);
            return new ResponseEntity<>(media, headers, HttpStatus.NOT_FOUND);
        } catch (IOException e) {
            e.printStackTrace();
            return new ResponseEntity<>(media, headers, HttpStatus.NOT_FOUND);
        }
    }
}
