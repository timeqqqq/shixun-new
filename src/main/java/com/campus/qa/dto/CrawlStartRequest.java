package com.campus.qa.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CrawlStartRequest {
    @NotBlank(message = "任务名称不能为空")
    private String taskName;

    @NotBlank(message = "URL不能为空")
    private String targetUrl;
}
