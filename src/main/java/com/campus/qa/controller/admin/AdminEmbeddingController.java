package com.campus.qa.controller.admin;

import com.campus.qa.service.QuestionEmbeddingService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/embeddings")
public class AdminEmbeddingController {

    private final QuestionEmbeddingService questionEmbeddingService;

    public AdminEmbeddingController(QuestionEmbeddingService questionEmbeddingService) {
        this.questionEmbeddingService = questionEmbeddingService;
    }

    @PostMapping("/rebuild")
    public Map<String, Object> rebuild() {
        QuestionEmbeddingService.RebuildStatus status = questionEmbeddingService.startRebuildAsync();
        return Map.of("status", status);
    }

    @GetMapping("/rebuild/status")
    public Map<String, Object> rebuildStatus() {
        return Map.of("status", questionEmbeddingService.rebuildStatus());
    }
}
