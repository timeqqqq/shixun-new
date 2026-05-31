package com.campus.qa.dto;

import lombok.Data;

@Data
public class ExcelImportResult {
    private int totalRows;
    private int successCount;
    private int skippedCount;
    private int overwrittenCount;
    private int failedCount;
}
