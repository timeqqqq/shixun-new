package com.campus.qa.vo;

import lombok.Data;

@Data
public class SearchResultItem {
    private Long questionId;
    private String question;
    private String answer;
    private String category;
    private double score;
}
