package com.campus.qa.dto.admin;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

@Data
public class BatchRejectRequest {
    @NotEmpty(message = "ids must not be empty")
    private List<Long> ids;

    private String reason;
}
