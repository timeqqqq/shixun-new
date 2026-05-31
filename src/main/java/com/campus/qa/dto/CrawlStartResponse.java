package com.campus.qa.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CrawlStartResponse {
    private Long taskId;
    private String status;
    private Integer totalFound;
    private Integer totalInserted;
    private String message;
}
