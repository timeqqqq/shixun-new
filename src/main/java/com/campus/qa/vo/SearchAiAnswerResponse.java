package com.campus.qa.vo;

import java.util.List;
import lombok.Data;

@Data
public class SearchAiAnswerResponse {
    private boolean enabled;
    private String model;
    private String answer;
    private String note;
    private List<String> referenceQuestions;
}
