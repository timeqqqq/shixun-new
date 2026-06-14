package com.campus.qa.controller.admin;

import com.campus.qa.service.HotService;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/hot")
public class AdminHotController {

    private final HotService hotService;

    public AdminHotController(HotService hotService) {
        this.hotService = hotService;
    }

    @PostMapping("/pin/{questionId}")
    public Map<String, String> pin(@PathVariable Long questionId) {
        hotService.pinQuestion(questionId);
        return Map.of("message", "pinned successfully");
    }
}
