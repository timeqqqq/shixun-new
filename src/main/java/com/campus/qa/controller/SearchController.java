package com.campus.qa.controller;

import com.campus.qa.service.SearchService;
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

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/search")
    public List<SearchResultItem> search(@RequestParam("q") String q, HttpServletRequest request) {
        return searchService.search(q, request.getRemoteAddr());
    }
}
