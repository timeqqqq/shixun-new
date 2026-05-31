package com.campus.qa.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("question")
public class Question {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String question;
    private String answer;
    private String category;
    private String source;

    @TableField("hit_count")
    private Long hitCount;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("is_pinned")
    private Integer isPinned;

    @TableField("pinned_order")
    private Integer pinnedOrder;
}
