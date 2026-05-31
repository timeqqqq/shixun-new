package com.campus.qa.dto.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SensitiveWordAddRequest {
    @NotBlank(message = "word must not be blank")
    private String word;

    private String level = "high";

    private Integer enabled = 1;

    private String source = "manual";
}
