package com.campus.qa.service.impl;

import com.campus.qa.config.AiAnswerProperties;
import com.campus.qa.service.AiAnswerService;
import com.campus.qa.service.SearchService;
import com.campus.qa.vo.SearchAiAnswerResponse;
import com.campus.qa.vo.SearchResultItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AiAnswerServiceImpl implements AiAnswerService {

    private final AiAnswerProperties properties;
    private final SearchService searchService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AiAnswerServiceImpl(AiAnswerProperties properties,
                               SearchService searchService,
                               ObjectMapper objectMapper) {
        this.properties = properties;
        this.searchService = searchService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Override
    public SearchAiAnswerResponse answer(String question) {
        SearchAiAnswerResponse response = new SearchAiAnswerResponse();
        response.setEnabled(isAvailable());
        response.setModel(properties.getModel());

        String q = question == null ? "" : question.trim();
        if (q.isEmpty()) {
            response.setNote("问题为空，无法生成润色答案。");
            return response;
        }

        List<SearchResultItem> results = searchService.searchForAiAnswer(q);
        if (results.isEmpty()) {
            response.setNote("当前知识库未检索到可供润色的参考答案。");
            return response;
        }

        response.setReferenceQuestions(results.stream()
                .limit(properties.getMaxReferences())
                .map(SearchResultItem::getQuestion)
                .toList());

        if (!isAvailable()) {
            response.setNote("AI 润色未启用，已返回检索结果供人工查看。");
            return response;
        }

        try {
            response.setAnswer(callChatModel(q, results));
            response.setNote("AI 润色严格基于已检索到的知识库内容生成。");
        } catch (Exception ex) {
            response.setNote("AI 润色失败，原因: " + ex.getMessage());
        }
        return response;
    }

    private String callChatModel(String question, List<SearchResultItem> results)
            throws IOException, InterruptedException {
        String prompt = buildPrompt(question, results);
        JsonNode payload = objectMapper.createObjectNode()
                .put("model", properties.getModel())
                .set("messages", objectMapper.createArrayNode()
                        .add(objectMapper.createObjectNode()
                                .put("role", "system")
                                .put("content", "你是校园知识库问答助手。只允许基于提供的参考答案进行润色，不要编造新事实。输出简洁、自然、适合学生阅读。"))
                        .add(objectMapper.createObjectNode()
                                .put("role", "user")
                                .put("content", prompt)));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(trimSlash(properties.getBaseUrl()) + "/v1/chat/completions"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + properties.getApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
            throw new IOException("chat api failed: http " + httpResponse.statusCode());
        }

        JsonNode root = objectMapper.readTree(httpResponse.body());
        String content = root.path("choices").path(0).path("message").path("content").asText();
        if (!StringUtils.hasText(content)) {
            throw new IOException("empty chat response");
        }
        return content.trim();
    }

    private String buildPrompt(String question, List<SearchResultItem> results) {
        List<String> sections = new ArrayList<>();
        sections.add("用户问题: " + question);
        sections.add("请仅根据以下参考问答生成一段自然语言回答，若信息不足请明确说明。");
        int index = 1;
        for (SearchResultItem item : results.stream().limit(properties.getMaxReferences()).toList()) {
            sections.add("参考" + index + " 问题: " + item.getQuestion());
            sections.add("参考" + index + " 答案: " + item.getAnswer());
            index++;
        }
        return String.join("\n", sections);
    }

    private boolean isAvailable() {
        return properties.isEnabled() && StringUtils.hasText(properties.getApiKey());
    }

    private static String trimSlash(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.endsWith("/") ? text.substring(0, text.length() - 1) : text;
    }
}
