package com.campus.qa.dto.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EditApproveRequest {
    @NotBlank(message = "question must not be blank")
    private String question;

    @NotBlank(message = "answer must not be blank")
    private String answer;

    private String category;
}
