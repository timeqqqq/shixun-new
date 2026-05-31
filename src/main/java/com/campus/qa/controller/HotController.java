package com.campus.qa.controller;

import com.campus.qa.service.HotService;
import com.campus.qa.vo.HotQuestionItem;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HotController {

    private final HotService hotService;

    public HotController(HotService hotService) {
        this.hotService = hotService;
    }

    @GetMapping("/hot")
    public List<HotQuestionItem> hot(@RequestParam(value = "period", defaultValue = "week") String period) {
        return hotService.listHot(period);
    }
}
