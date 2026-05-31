package com.campus.qa.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("crawl_task")
public class CrawlTask {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("task_name")
    private String taskName;

    @TableField("target_url")
    private String targetUrl;

    private String status;

    @TableField("total_found")
    private Integer totalFound;

    @TableField("total_inserted")
    private Integer totalInserted;

    @TableField("start_time")
    private LocalDateTime startTime;

    @TableField("end_time")
    private LocalDateTime endTime;

    @TableField("error_message")
    private String errorMessage;
}
