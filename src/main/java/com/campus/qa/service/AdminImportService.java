package com.campus.qa.service;

import com.campus.qa.dto.ExcelImportResult;
import org.springframework.web.multipart.MultipartFile;

public interface AdminImportService {
    ExcelImportResult importQuestionsFromExcel(MultipartFile file, String duplicateStrategy);

    byte[] buildTemplateFile();
}
