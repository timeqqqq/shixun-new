package com.campus.qa.controller;

import com.campus.qa.service.SearchService;
import com.campus.qa.service.AiAnswerService;
import com.campus.qa.vo.SearchAiAnswerResponse;
import com.campus.qa.vo.SearchResultItem;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SearchController {

    private final SearchService searchService;
    private final AiAnswerService aiAnswerService;

    public SearchController(SearchService searchService, AiAnswerService aiAnswerService) {
        this.searchService = searchService;
        this.aiAnswerService = aiAnswerService;
    }

    @GetMapping("/search")
    public List<SearchResultItem> search(@RequestParam("q") String q, HttpServletRequest request) {
        return searchService.search(q, request.getRemoteAddr());
    }

    @GetMapping("/search/answer")
    public SearchAiAnswerResponse answer(@RequestParam("q") String q) {
        return aiAnswerService.answer(q);
    }
}
