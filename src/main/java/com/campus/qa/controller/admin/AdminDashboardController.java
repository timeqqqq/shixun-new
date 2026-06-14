package com.campus.qa.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.campus.qa.entity.Contribution;
import com.campus.qa.entity.CrawlTask;
import com.campus.qa.entity.Question;
import com.campus.qa.entity.QuestionEmbedding;
import com.campus.qa.entity.SensitiveWord;
import com.campus.qa.mapper.ContributionMapper;
import com.campus.qa.mapper.CrawlTaskMapper;
import com.campus.qa.mapper.QuestionEmbeddingMapper;
import com.campus.qa.mapper.QuestionMapper;
import com.campus.qa.mapper.SensitiveWordMapper;
import com.campus.qa.service.HotService;
import com.campus.qa.service.QuestionEmbeddingService;
import com.campus.qa.service.stats.CacheStatsService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    private final QuestionMapper questionMapper;
    private final ContributionMapper contributionMapper;
    private final CrawlTaskMapper crawlTaskMapper;
    private final QuestionEmbeddingMapper questionEmbeddingMapper;
    private final SensitiveWordMapper sensitiveWordMapper;
    private final CacheStatsService cacheStatsService;
    private final HotService hotService;
    private final QuestionEmbeddingService questionEmbeddingService;

    public AdminDashboardController(QuestionMapper questionMapper,
                                    ContributionMapper contributionMapper,
                                    CrawlTaskMapper crawlTaskMapper,
                                    QuestionEmbeddingMapper questionEmbeddingMapper,
                                    SensitiveWordMapper sensitiveWordMapper,
                                    CacheStatsService cacheStatsService,
                                    HotService hotService,
                                    QuestionEmbeddingService questionEmbeddingService) {
        this.questionMapper = questionMapper;
        this.contributionMapper = contributionMapper;
        this.crawlTaskMapper = crawlTaskMapper;
        this.questionEmbeddingMapper = questionEmbeddingMapper;
        this.sensitiveWordMapper = sensitiveWordMapper;
        this.cacheStatsService = cacheStatsService;
        this.hotService = hotService;
        this.questionEmbeddingService = questionEmbeddingService;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        long questionCount = questionMapper.selectCount(new QueryWrapper<Question>());
        long pendingContributionCount = contributionMapper.selectCount(
                new QueryWrapper<Contribution>().eq("status", "pending"));
        long approvedContributionCount = contributionMapper.selectCount(
                new QueryWrapper<Contribution>().eq("status", "approved"));
        long embeddingCount = questionEmbeddingMapper.selectCount(new QueryWrapper<QuestionEmbedding>());
        long sensitiveWordCount = sensitiveWordMapper.selectCount(
                new QueryWrapper<SensitiveWord>().eq("enabled", 1));
        long crawlTaskCount = crawlTaskMapper.selectCount(new QueryWrapper<CrawlTask>());
        List<?> hotList = hotService.listHot("week");

        return Map.of(
                "questionCount", questionCount,
                "pendingContributionCount", pendingContributionCount,
                "approvedContributionCount", approvedContributionCount,
                "embeddingCount", embeddingCount,
                "embeddingEnabled", questionEmbeddingService.isEnabled(),
                "sensitiveWordCount", sensitiveWordCount,
                "crawlTaskCount", crawlTaskCount,
                "cacheStats", cacheStatsService.snapshot(),
                "hotQuestions", hotList
        );
    }
}
