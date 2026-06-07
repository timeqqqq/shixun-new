package com.campus.qa.vo;

import java.io.Serializable;
import java.util.List;
import lombok.Data;

@Data
public class SearchResultItem implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long questionId;
    private String question;
    private String answer;
    private String category;
    private double score;
    private List<String> matchedTerms;
}
