package com.campus.qa.controller.admin;

import com.campus.qa.dto.admin.SensitiveWordAddRequest;
import com.campus.qa.entity.SensitiveWord;
import com.campus.qa.service.SensitiveWordService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/sensitive-words")
public class AdminSensitiveWordController {

    private final SensitiveWordService sensitiveWordService;

    public AdminSensitiveWordController(SensitiveWordService sensitiveWordService) {
        this.sensitiveWordService = sensitiveWordService;
    }

    @GetMapping
    public List<SensitiveWord> list() {
        return sensitiveWordService.listAll();
    }

    @PostMapping
    public SensitiveWord add(@Valid @RequestBody SensitiveWordAddRequest request) {
        return sensitiveWordService.add(request.getWord(), request.getLevel(), request.getEnabled(), request.getSource());
    }

    @DeleteMapping("/{id}")
    public Map<String, String> delete(@PathVariable Long id) {
        sensitiveWordService.deleteById(id);
        return Map.of("message", "deleted");
    }

    @PutMapping("/{id}/enabled")
    public Map<String, String> setEnabled(@PathVariable Long id, @RequestParam("value") Integer value) {
        sensitiveWordService.setEnabled(id, value);
        return Map.of("message", "updated");
    }

    @PostMapping("/refresh")
    public Map<String, Object> refresh() {
        int loaded = sensitiveWordService.refresh();
        return Map.of("message", "refreshed", "loaded", loaded);
    }
}
