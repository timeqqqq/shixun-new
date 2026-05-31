package com.campus.qa.controller.admin;

import com.campus.qa.service.stats.CacheStatsService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/cache")
public class AdminCacheController {

    private final CacheStatsService cacheStatsService;

    public AdminCacheController(CacheStatsService cacheStatsService) {
        this.cacheStatsService = cacheStatsService;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return cacheStatsService.snapshot();
    }
}
