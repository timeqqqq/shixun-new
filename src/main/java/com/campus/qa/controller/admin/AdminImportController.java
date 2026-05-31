package com.campus.qa.controller.admin;

import com.campus.qa.dto.ExcelImportResult;
import com.campus.qa.service.AdminImportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/import")
public class AdminImportController {

    private final AdminImportService adminImportService;

    public AdminImportController(AdminImportService adminImportService) {
        this.adminImportService = adminImportService;
    }

    @PostMapping("/excel")
    public ExcelImportResult importExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "duplicateStrategy", defaultValue = "skip") String duplicateStrategy) {
        return adminImportService.importQuestionsFromExcel(file, duplicateStrategy);
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate() {
        byte[] content = adminImportService.buildTemplateFile();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=question_import_template.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(content);
    }
}
