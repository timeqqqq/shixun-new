package com.campus.qa.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("contribution")
public class Contribution {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String question;
    private String answer;
    private String category;
    private String status;

    @TableField("submit_ip")
    private String submitIp;

    @TableField("submit_time")
    private LocalDateTime submitTime;

    @TableField("audit_time")
    private LocalDateTime auditTime;

    @TableField("reject_reason")
    private String rejectReason;
}
