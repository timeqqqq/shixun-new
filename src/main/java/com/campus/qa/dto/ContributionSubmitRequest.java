package com.campus.qa.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ContributionSubmitRequest {
    @NotBlank(message = "question must not be blank")
    private String question;

    @NotBlank(message = "answer must not be blank")
    private String answer;

    private String category;
}
