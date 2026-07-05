package com.campus.qa.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CrawlStartRequest {
    @NotBlank(message = "task name must not be blank")
    private String taskName;

    @NotBlank(message = "target url must not be blank")
    private String targetUrl;

    private String category;

    private Boolean batchMode = Boolean.FALSE;

    private Integer maxPages = 10;
}
