package dev.niceone.smartsiteappserver.dto;

import lombok.Data;

import java.util.List;

@Data
public class AuthinLogPageDto {
    private List<AuthinLogDto> data;
    private int page;
    private int size;
    private int totalPages;
    private int totalElements;
}
