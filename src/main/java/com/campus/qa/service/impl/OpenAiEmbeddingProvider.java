package com.campus.qa.service.impl;

import com.campus.qa.config.EmbeddingProperties;
import com.campus.qa.service.EmbeddingProvider;
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
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class OpenAiEmbeddingProvider implements EmbeddingProvider {

    private final EmbeddingProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiEmbeddingProvider(EmbeddingProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, properties.getConnectTimeoutSeconds())))
                .build();
    }

    @Override
    public boolean isAvailable() {
        return properties.isEnabled()
                && "openai".equalsIgnoreCase(properties.getProvider())
                && StringUtils.hasText(properties.getApiKey());
    }

    @Override
    public String modelName() {
        return properties.getModel();
    }

    @Override
    public List<Double> embed(String text) throws IOException, InterruptedException {
        if (!isAvailable()) {
            return List.of();
        }
        String payload = objectMapper.createObjectNode()
                .put("model", properties.getModel())
                .put("input", text == null ? "" : text)
                .toString();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(trimSlash(properties.getBaseUrl()) + "/v1/embeddings"))
                .timeout(Duration.ofSeconds(Math.max(1, properties.getRequestTimeoutSeconds())))
                .header("Authorization", "Bearer " + properties.getApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("embedding api failed: http " + response.statusCode() + ", body=" + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode vectorNode = root.path("data").path(0).path("embedding");
        if (!vectorNode.isArray() || vectorNode.isEmpty()) {
            throw new IOException("embedding api returned empty vector");
        }

        List<Double> vector = new ArrayList<>(vectorNode.size());
        for (JsonNode item : vectorNode) {
            vector.add(item.asDouble());
        }
        return vector;
    }

    private static String trimSlash(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.endsWith("/") ? text.substring(0, text.length() - 1) : text;
    }
}
