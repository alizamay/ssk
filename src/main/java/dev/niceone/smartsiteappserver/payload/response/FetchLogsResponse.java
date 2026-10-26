package dev.niceone.smartsiteappserver.payload.response;


import dev.niceone.smartsiteappserver.model.LogEntry;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class FetchLogsResponse {

    private List<LogModel> logs; // Adjust this if LogEntry is not the correct type
    private long totalCount;
    private int totalPages;
    private int currentPage;
    private String nextPageToken;

    // Getters and Setters
    public List<LogModel> getLogs() {
        return logs;
    }

    public void setLogs(List<LogModel> logs) {
        this.logs = logs;
    }

    public long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(long totalCount) {
        this.totalCount = totalCount;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(int currentPage) {
        this.currentPage = currentPage;
    }

    public String getNextPageToken() {
        return nextPageToken;
    }

    public void setNextPageToken(String nextPageToken) {
        this.nextPageToken = nextPageToken;
    }
}


