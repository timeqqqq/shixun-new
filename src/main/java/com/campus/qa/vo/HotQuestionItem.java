package com.campus.qa.vo;

import lombok.Data;

@Data
public class HotQuestionItem {
    private Long questionId;
    private String question;
    private String category;
    private Long queryCount;
    private boolean pinned;
}
