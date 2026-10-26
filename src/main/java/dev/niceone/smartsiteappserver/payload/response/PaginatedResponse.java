package dev.niceone.smartsiteappserver.payload.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class PaginatedResponse<T> {
    private List<T> data;
    private int page;
    private int size;
    private int totalPages;
    private int totalElements;

    // Getters and Setters
}