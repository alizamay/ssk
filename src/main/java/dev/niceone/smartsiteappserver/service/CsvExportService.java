package dev.niceone.smartsiteappserver.service;

import com.opencsv.CSVWriter;
import dev.niceone.smartsiteappserver.model.AuthinLogs;
import org.springframework.stereotype.Service;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.List;

@Service
public class CsvExportService {

    public void exportToCsv(List<AuthinLogs> logs, String filePath) throws IOException {
        try (Writer writer = new FileWriter(filePath);
             CSVWriter csvWriter = new CSVWriter(writer)) {

            // Header
            csvWriter.writeNext(new String[]{"site_code", "message", "is_clear", "timestamp"});

            for (AuthinLogs log : logs) {

                String statusName = "Create";

                if(log.getIsClear()){
                    statusName = "Clear";
                }

                csvWriter.writeNext(new String[]{
                        log.getSiteCode(),
                        log.getMessage(),
                        statusName,
                        log.getTimestamp().toString()
                });
            }
        }
    }
}
