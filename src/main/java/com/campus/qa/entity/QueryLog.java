package com.campus.qa.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("query_log")
public class QueryLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String keyword;

    @TableField("matched_question_id")
    private Long matchedQuestionId;

    @TableField("user_ip")
    private String userIp;

    @TableField("query_time")
    private LocalDateTime queryTime;
}
