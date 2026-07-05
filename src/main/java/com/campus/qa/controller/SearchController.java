package com.campus.qa.controller;

import com.campus.qa.service.SearchService;
import com.campus.qa.service.AiAnswerService;
import com.campus.qa.vo.SearchAiAnswerResponse;
import com.campus.qa.vo.SearchResultItem;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SearchController {

    private final SearchService searchService;
    private final AiAnswerService aiAnswerService;
    private final HttpClient httpClient;

    public SearchController(SearchService searchService, AiAnswerService aiAnswerService) {
        this.searchService = searchService;
        this.aiAnswerService = aiAnswerService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @GetMapping("/search")
    public List<SearchResultItem> search(@RequestParam("q") String q, HttpServletRequest request) {
        return searchService.search(q, request.getRemoteAddr());
    }

    @GetMapping("/search/answer")
    public SearchAiAnswerResponse answer(@RequestParam("q") String q) {
        return aiAnswerService.answer(q);
    }

    @GetMapping("/image")
    public ResponseEntity<byte[]> proxyImage(@RequestParam("url") String url) throws IOException, InterruptedException {
        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        URI uri = URI.create(url.trim());
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        if (!(host.endsWith("scut.edu.cn") || host.endsWith("www2.scut.edu.cn"))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", "https://www.scut.edu.cn/")
                .GET()
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return ResponseEntity.status(response.statusCode()).build();
        }

        String contentType = response.headers().firstValue("Content-Type").orElse(MediaType.IMAGE_PNG_VALUE);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                .contentType(MediaType.parseMediaType(contentType))
                .body(response.body());
    }
}
