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
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AiAnswerServiceImpl implements AiAnswerService {

    private static final String INSUFFICIENT_TEXT = "知识库暂未提供完整信息。";
    private static final String SYSTEM_PROMPT = """
            你是校园生活知识库问答助手。
            你的回答必须满足以下要求：
            1. 只能依据提供的参考资料作答，禁止编造。
            2. 使用自然、简洁、完整的中文。
            3. 先直接回答用户问题，再补充必要的时间、地点、条件或联系方式。
            4. 如果参考资料不足以支持明确结论，只输出：知识库暂未提供完整信息。
            5. 不要输出列表，不要输出残缺句子，不要照抄脚本或网页导航文字。
            """;
    private static final int MIN_ANSWER_LENGTH = 12;
    private static final Set<String> INTENT_WORDS = Set.of(
            "时间", "几点", "什么时候", "地点", "哪里", "地址", "电话", "怎么", "流程",
            "预约", "挂号", "报销", "条件", "费用", "材料", "开放", "关闭", "门诊", "联系"
    );
    private static final Set<String> CONTACT_HINTS = Set.of("联系", "电话", "手机号", "手机", "地址", "邮箱", "email", "qq", "微信");

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

        String query = question == null ? "" : question.trim();
        if (query.isEmpty()) {
            response.setNote("问题为空，未生成 AI 回答。");
            return response;
        }

        List<SearchResultItem> results = searchService.searchForAiAnswer(query);
        if (results.isEmpty()) {
            response.setNote("知识库中没有找到可供整理的参考内容。");
            return response;
        }

        List<SearchResultItem> references = chooseReferences(query, results);
        response.setReferenceQuestions(references.stream().map(SearchResultItem::getQuestion).toList());

        String fallback = buildDeterministicFallback(query, references);
        if (!isAvailable()) {
            if (StringUtils.hasText(fallback)) {
                response.setAnswer(fallback);
                response.setNote("未配置 AI 接口，当前展示程序整理结果。");
            } else {
                response.setNote("未配置 AI 接口，当前仅展示知识库原始结果。");
            }
            return response;
        }

        String answer = tryGenerateAnswer(query, references);
        if (isUsableAnswer(answer)) {
            response.setAnswer(answer);
            response.setNote("AI 已基于检索结果整理回答。");
            return response;
        }

        if (StringUtils.hasText(fallback)) {
            response.setAnswer(fallback);
            response.setNote("AI 输出不稳定，已回退为程序整理结果。");
            return response;
        }

        response.setNote("AI 未生成稳定结果，当前回退为知识库原始答案。");
        return response;
    }

    private List<SearchResultItem> chooseReferences(String question, List<SearchResultItem> results) {
        List<SearchResultItem> cleaned = results.stream()
                .filter(item -> StringUtils.hasText(item.getRawAnswer()))
                .filter(item -> !looksLikeScript(item.getRawAnswer()))
                .sorted(Comparator.comparingDouble(SearchResultItem::getScore).reversed())
                .toList();
        if (cleaned.isEmpty()) {
            return List.of();
        }
        if (isBroadQuery(question) || cleaned.get(0).getScore() >= 90.0) {
            return List.of(cleaned.get(0));
        }
        return cleaned.stream().limit(Math.min(2, Math.max(1, properties.getMaxReferences()))).toList();
    }

    private boolean isBroadQuery(String question) {
        String q = question == null ? "" : question.trim();
        if (q.length() > 6) {
            return false;
        }
        for (String word : INTENT_WORDS) {
            if (q.contains(word)) {
                return false;
            }
        }
        return true;
    }

    private String tryGenerateAnswer(String question, List<SearchResultItem> references) {
        if (references.isEmpty()) {
            return "";
        }
        try {
            return callChatModel(question, references);
        } catch (Exception ignore) {
            return "";
        }
    }

    private String callChatModel(String question, List<SearchResultItem> references)
            throws IOException, InterruptedException {
        String prompt = buildPrompt(question, references);
        JsonNode payload = objectMapper.createObjectNode()
                .put("model", properties.getModel())
                .put("temperature", 0.1)
                .set("messages", objectMapper.createArrayNode()
                        .add(objectMapper.createObjectNode()
                                .put("role", "system")
                                .put("content", SYSTEM_PROMPT))
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

        HttpResponse<String> httpResponse =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
            throw new IOException("chat api failed: http " + httpResponse.statusCode());
        }

        JsonNode root = objectMapper.readTree(httpResponse.body());
        String content = root.path("choices").path(0).path("message").path("content").asText();
        if (!StringUtils.hasText(content)) {
            throw new IOException("empty chat response");
        }
        return normalizeAnswer(content);
    }

    private String buildPrompt(String question, List<SearchResultItem> references) {
        List<String> sections = new ArrayList<>();
        sections.add("用户问题：" + question);
        sections.add("请优先根据最相关的参考资料直接回答，不要复述网页导航文字，不要输出代码。");
        sections.add("如果不同参考资料之间存在冲突，优先采用标题更直接、匹配度更高的内容。");
        sections.add("如果资料不足，请只输出：" + INSUFFICIENT_TEXT);

        int index = 1;
        for (SearchResultItem item : references) {
            String content = buildReferenceContext(question, item.getRawAnswer());
            if (!StringUtils.hasText(content)) {
                continue;
            }
            sections.add("参考资料" + index + "标题：" + item.getQuestion());
            sections.add("参考资料" + index + "内容：" + content);
            index++;
        }
        return String.join("\n", sections);
    }

    private String buildReferenceContext(String question, String answer) {
        String clean = SearchServiceImpl.cleanReferenceText(answer, 1200);
        if (!StringUtils.hasText(clean)) {
            return "";
        }
        List<String> sentences = splitSentences(clean);
        Set<String> tokens = extractQueryTokens(question);
        boolean contactIntent = isContactIntent(question);
        if (contactIntent) {
            tokens.addAll(CONTACT_HINTS);
        }

        List<Snippet> snippets = new ArrayList<>();
        for (String sentence : sentences) {
            int score = scoreSentence(sentence, tokens, contactIntent);
            if (score > 0) {
                snippets.add(new Snippet(sentence, score));
            }
        }
        snippets.sort(Comparator.comparingInt(Snippet::score).reversed());
        if (snippets.isEmpty()) {
            return clean.length() > 260 ? clean.substring(0, 260) : clean;
        }

        LinkedHashSet<String> selected = new LinkedHashSet<>();
        for (Snippet snippet : snippets) {
            selected.add(snippet.text());
            if (selected.size() >= 3) {
                break;
            }
        }
        return String.join("；", selected);
    }

    private String buildDeterministicFallback(String question, List<SearchResultItem> references) {
        List<String> segments = new ArrayList<>();
        Set<String> tokens = extractQueryTokens(question);
        boolean contactIntent = isContactIntent(question);
        if (contactIntent) {
            tokens.addAll(CONTACT_HINTS);
        }

        for (SearchResultItem item : references) {
            String clean = SearchServiceImpl.cleanReferenceText(item.getRawAnswer(), 1200);
            if (!StringUtils.hasText(clean) || looksLikeScript(clean)) {
                continue;
            }
            List<String> sentences = splitSentences(clean);
            List<Snippet> snippets = new ArrayList<>();
            for (String sentence : sentences) {
                int score = scoreSentence(sentence, tokens, contactIntent);
                if (score > 0) {
                    snippets.add(new Snippet(sentence, score));
                }
            }
            snippets.sort(Comparator.comparingInt(Snippet::score).reversed());
            if (!snippets.isEmpty()) {
                segments.add(snippets.get(0).text());
            }
            if (segments.size() >= 2) {
                break;
            }
        }

        if (segments.isEmpty()) {
            return "";
        }
        String answer = String.join("；", new LinkedHashSet<>(segments));
        if (answer.length() > 180) {
            answer = answer.substring(0, 180) + "…";
        }
        if (!answer.endsWith("。") && !answer.endsWith("；") && !answer.endsWith("！") && !answer.endsWith("…")) {
            answer = answer + "。";
        }
        return answer;
    }

    private static List<String> splitSentences(String text) {
        List<String> result = new ArrayList<>();
        for (String item : text.split("[。！？；;\\n\\r]+")) {
            String sentence = item == null ? "" : item.trim();
            if (sentence.length() >= 8 && !looksLikeScript(sentence)) {
                result.add(sentence);
            }
        }
        return result;
    }

    private static Set<String> extractQueryTokens(String question) {
        String normalized = question == null ? "" : question.replaceAll("[^\\p{IsHan}A-Za-z0-9]", "");
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        if (normalized.length() >= 2) {
            tokens.add(normalized);
        }
        for (int i = 0; i < normalized.length() - 1; i++) {
            tokens.add(normalized.substring(i, i + 2));
            if (i + 3 <= normalized.length()) {
                tokens.add(normalized.substring(i, i + 3));
            }
        }
        return tokens;
    }

    private static int scoreSentence(String sentence, Set<String> tokens, boolean contactIntent) {
        int score = 0;
        for (String token : tokens) {
            if (token.length() >= 2 && sentence.contains(token)) {
                score += Math.max(2, token.length());
            }
        }
        if (sentence.contains("时间") || sentence.contains("地点") || sentence.contains("地址")
                || sentence.contains("电话") || sentence.contains("流程")
                || sentence.contains("门诊") || sentence.contains("报销")
                || sentence.contains("开放") || sentence.contains("关闭")) {
            score += 2;
        }
        if (contactIntent && containsPhoneOrAddress(sentence)) {
            score += 4;
        }
        if (sentence.length() > 140) {
            score -= 1;
        }
        return Math.max(score, 0);
    }

    private static String normalizeAnswer(String text) {
        String value = text == null ? "" : text;
        value = value.replace('\u00A0', ' ');
        value = value.replaceAll("[\\u0000-\\u001f]", "");
        value = value.replaceAll("\\s+", " ").trim();
        value = value.replaceAll("^[-*\\d.\\s]+", "");
        value = value.replaceAll("^(回答|答复)[:：\\s]*", "");
        value = value.trim();
        if (value.endsWith("：") || value.endsWith(":")) {
            return "";
        }
        return value;
    }

    private static boolean isUsableAnswer(String answer) {
        if (!StringUtils.hasText(answer)) {
            return false;
        }
        String value = answer.trim();
        if (value.length() < MIN_ANSWER_LENGTH) {
            return false;
        }
        if (!value.matches(".*[\\p{IsHan}].*")) {
            return false;
        }
        if (value.contains(INSUFFICIENT_TEXT)) {
            return false;
        }
        return !looksLikeScript(value);
    }

    private boolean isAvailable() {
        return properties.isEnabled() && StringUtils.hasText(properties.getApiKey());
    }

    private static boolean isContactIntent(String question) {
        String q = question == null ? "" : question.trim().toLowerCase(Locale.ROOT);
        for (String hint : CONTACT_HINTS) {
            if (q.contains(hint.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsPhoneOrAddress(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        return text.contains("电话") || text.contains("地址") || text.contains("邮箱")
                || text.contains("联系") || text.matches(".*\\d{7,}.*");
    }

    private static boolean looksLikeScript(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("function(")
                || lower.contains("function ")
                || lower.contains("window.location")
                || lower.contains("document.")
                || lower.contains("alert(")
                || lower.contains("pagenum")
                || lower.contains("return false")
                || (text.length() > 50 && text.chars().filter(ch -> ch == '{' || ch == '}' || ch == ';' || ch == '=').count() >= 8);
    }

    private static String trimSlash(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.endsWith("/") ? text.substring(0, text.length() - 1) : text;
    }

    private record Snippet(String text, int score) {
    }
}
