package com.campus.qa.dto.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RejectRequest {
    @NotBlank(message = "reason must not be blank")
    private String reason;
}
