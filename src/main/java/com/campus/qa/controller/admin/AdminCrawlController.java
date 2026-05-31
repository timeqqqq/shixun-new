package com.campus.qa.controller.admin;

import com.campus.qa.dto.CrawlStartRequest;
import com.campus.qa.dto.CrawlStartResponse;
import com.campus.qa.entity.CrawlTask;
import com.campus.qa.service.CrawlService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/crawl")
public class AdminCrawlController {

    private final CrawlService crawlService;

    public AdminCrawlController(CrawlService crawlService) {
        this.crawlService = crawlService;
    }

    @PostMapping("/start")
    public CrawlStartResponse start(@Valid @RequestBody CrawlStartRequest request) {
        return crawlService.startCrawl(request);
    }

    @GetMapping("/tasks")
    public List<CrawlTask> tasks() {
        return crawlService.listTasks();
    }
}
