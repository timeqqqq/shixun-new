package com.campus.qa.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.qa.config.EmbeddingProperties;
import com.campus.qa.entity.Question;
import com.campus.qa.mapper.QuestionMapper;
import com.campus.qa.service.QueryLogAsyncService;
import com.campus.qa.service.QuestionEmbeddingService;
import com.campus.qa.service.SearchService;
import com.campus.qa.service.stats.CacheStatsService;
import com.campus.qa.vo.SearchResultItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huaban.analysis.jieba.JiebaSegmenter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SearchServiceImpl implements SearchService {

    private static final String IMAGE_PREFIX = "IMAGE::";

    private static final Map<String, List<String>> SYNONYM_MAP = Map.ofEntries(
            Map.entry("食堂", List.of("餐厅", "饭堂")),
            Map.entry("图书馆", List.of("阅览室", "自习室")),
            Map.entry("打印", List.of("复印", "文印")),
            Map.entry("校车", List.of("班车", "接驳车")),
            Map.entry("宿舍", List.of("公寓", "寝室")),
            Map.entry("报修", List.of("维修", "修理")),
            Map.entry("校医院", List.of("医院", "门诊", "医务室")),
            Map.entry("医保", List.of("医疗保险", "报销")),
            Map.entry("挂号", List.of("预约", "门诊预约")),
            Map.entry("体检", List.of("健康体检", "检查")),
            Map.entry("联系方式", List.of("电话", "地址", "邮箱", "联系")),
            Map.entry("校徽", List.of("学校标识", "校标", "logo", "徽标")),
            Map.entry("校名", List.of("学校名称", "中英文校名")),
            Map.entry("校训", List.of("学校精神", "办学理念"))
    );

    private static final Map<String, String> TYPO_MAP = Map.ofEntries(
            Map.entry("饭堂", "食堂"),
            Map.entry("图书管", "图书馆"),
            Map.entry("医宝", "医保"),
            Map.entry("医报", "医保"),
            Map.entry("校医", "校医院"),
            Map.entry("复印店", "打印"),
            Map.entry("学校logo", "校徽")
    );

    private static final Set<String> NOTICE_WORDS = Set.of("通知", "公告", "新闻", "动态", "简报");
    private static final Set<String> CONTACT_HINTS = Set.of("联系", "电话", "手机号", "手机", "地址", "邮箱", "email", "qq", "微信");
    private static final Set<String> MEDICAL_HINTS = Set.of("医保", "报销", "保险", "门诊", "校医院", "医院", "就诊", "挂号");
    private static final Set<String> VISUAL_HINTS = Set.of("校徽", "校标", "学校标识", "logo", "徽标", "校名", "学校名称");

    private static final List<String> NOISE_MARKERS = List.of(
            "首页", "联系我们", "新闻动态", "通知公告", "查看更多", "上一篇", "下一篇",
            "版权所有", "版权", "ICP", "附件下载", "返回顶部"
    );
    private static final List<String> SCRIPT_MARKERS = List.of(
            "function(", "function ", "var ", "let ", "const ", "window.location", "document.",
            "getElementById", "alert(", "pageNum", "return false", "$(", "layui", "onclick"
    );

    private static final Pattern SPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern BROKEN_CHAR_PATTERN = Pattern.compile("[\\uFFFD]");
    private static final Pattern CONTROL_PATTERN = Pattern.compile("[\\u0000-\\u001f]");
    private static final int PREVIEW_MAX_LENGTH = 220;
    private static final int FUZZY_SCAN_LIMIT = 300;

    private final QuestionMapper questionMapper;
    private final QueryLogAsyncService queryLogAsyncService;
    private final CacheStatsService cacheStatsService;
    private final RedisTemplate<Object, Object> redisTemplate;
    private final QuestionEmbeddingService questionEmbeddingService;
    private final EmbeddingProperties embeddingProperties;
    private final ObjectMapper objectMapper;
    private final JiebaSegmenter jiebaSegmenter = new JiebaSegmenter();

    public SearchServiceImpl(QuestionMapper questionMapper,
                             QueryLogAsyncService queryLogAsyncService,
                             CacheStatsService cacheStatsService,
                             RedisTemplate<Object, Object> redisTemplate,
                             QuestionEmbeddingService questionEmbeddingService,
                             EmbeddingProperties embeddingProperties,
                             ObjectMapper objectMapper) {
        this.questionMapper = questionMapper;
        this.queryLogAsyncService = queryLogAsyncService;
        this.cacheStatsService = cacheStatsService;
        this.redisTemplate = redisTemplate;
        this.questionEmbeddingService = questionEmbeddingService;
        this.embeddingProperties = embeddingProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<SearchResultItem> search(String keyword, String userIp) {
        return doSearch(keyword, userIp, true, true, true);
    }

    @Override
    public List<SearchResultItem> searchForAiAnswer(String keyword) {
        return doSearch(keyword, null, false, false, false);
    }

    private List<SearchResultItem> doSearch(String keyword,
                                            String userIp,
                                            boolean recordStats,
                                            boolean recordLog,
                                            boolean previewAnswer) {
        String query = normalizeQuery(keyword);
        if (query.isEmpty()) {
            return List.of();
        }
        if (recordStats) {
            cacheStatsService.incTotal();
        }

        String cacheKey = "search:" + query;
        if (previewAnswer) {
            List<SearchResultItem> cached = readCache(cacheKey);
            if (cached != null) {
                if (recordStats) {
                    cacheStatsService.incHit();
                }
                if (recordLog) {
                    Long topId = cached.isEmpty() ? null : cached.get(0).getQuestionId();
                    queryLogAsyncService.saveLogAsync(query, topId, userIp);
                }
                return cached;
            }
        }

        List<String> segmentedTokens = tokenize(query);
        List<String> queryTokens = expandTokens(query, segmentedTokens.isEmpty() ? List.of(query) : segmentedTokens);
        Map<Long, Double> semanticScoreMap = searchSemanticScores(query);

        Map<Long, Question> candidateMap = new LinkedHashMap<>();
        for (Question question : findKeywordCandidates(queryTokens)) {
            candidateMap.put(question.getId(), question);
        }
        if (!semanticScoreMap.isEmpty()) {
            candidateMap.putAll(loadQuestionsByIds(semanticScoreMap.keySet()));
        }
        if (candidateMap.size() < 8) {
            for (Question item : findFuzzyCandidates()) {
                candidateMap.putIfAbsent(item.getId(), item);
            }
        }

        List<SearchResultItem> ranked = rank(new ArrayList<>(candidateMap.values()), query, queryTokens, semanticScoreMap, previewAnswer);
        List<SearchResultItem> top5 = ranked.size() > 5 ? new ArrayList<>(ranked.subList(0, 5)) : ranked;

        if (previewAnswer && !top5.isEmpty()) {
            writeCache(cacheKey, top5);
        }
        if (recordLog) {
            Long topId = top5.isEmpty() ? null : top5.get(0).getQuestionId();
            queryLogAsyncService.saveLogAsync(query, topId, userIp);
        }
        return top5;
    }

    private List<SearchResultItem> readCache(String cacheKey) {
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached instanceof List<?>) {
                List<SearchResultItem> result = new ArrayList<>();
                for (Object item : (List<?>) cached) {
                    if (item instanceof SearchResultItem searchResultItem) {
                        result.add(searchResultItem);
                    } else {
                        result.add(objectMapper.convertValue(item, SearchResultItem.class));
                    }
                }
                return result;
            }
        } catch (Exception ignore) {
            // ignore cache read failures
        }
        return null;
    }

    private void writeCache(String cacheKey, List<SearchResultItem> data) {
        try {
            redisTemplate.opsForValue().set(cacheKey, data, Duration.ofMinutes(5));
        } catch (Exception ignore) {
            // ignore cache write failures
        }
    }

    private Map<Long, Double> searchSemanticScores(String keyword) {
        List<QuestionEmbeddingService.SemanticSearchHit> hits =
                questionEmbeddingService.searchSimilar(keyword, embeddingProperties.getSemanticTopN());
        Map<Long, Double> scoreMap = new HashMap<>();
        for (QuestionEmbeddingService.SemanticSearchHit hit : hits) {
            scoreMap.put(hit.questionId(), hit.score());
        }
        return scoreMap;
    }

    private List<Question> findKeywordCandidates(List<String> queryTokens) {
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> {
            for (int i = 0; i < queryTokens.size(); i++) {
                String token = queryTokens.get(i);
                if (i > 0) {
                    w.or();
                }
                w.like(Question::getQuestion, token)
                        .or()
                        .like(Question::getAnswer, token)
                        .or()
                        .like(Question::getCategory, token);
            }
        });
        wrapper.last("LIMIT " + embeddingProperties.getCandidateLimit());
        return questionMapper.selectList(wrapper);
    }

    private List<Question> findFuzzyCandidates() {
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Question::getHitCount);
        wrapper.orderByDesc(Question::getId);
        wrapper.last("LIMIT " + FUZZY_SCAN_LIMIT);
        return questionMapper.selectList(wrapper);
    }

    private Map<Long, Question> loadQuestionsByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<Question> questions = questionMapper.selectBatchIds(ids);
        return questions.stream().collect(Collectors.toMap(Question::getId, item -> item));
    }

    private List<SearchResultItem> rank(List<Question> candidates,
                                        String keyword,
                                        List<String> tokens,
                                        Map<Long, Double> semanticScoreMap,
                                        boolean previewAnswer) {
        List<SearchResultItem> result = new ArrayList<>();
        String queryLower = safeLower(keyword);
        boolean medicalIntent = containsAny(keyword, MEDICAL_HINTS);
        boolean contactIntent = containsAny(keyword, CONTACT_HINTS);
        boolean visualIntent = containsAny(keyword, VISUAL_HINTS);

        for (Question item : candidates) {
            String questionText = defaultText(item.getQuestion());
            String answerText = defaultText(item.getAnswer());
            String categoryText = defaultText(item.getCategory());
            String questionLower = safeLower(questionText);
            String answerLower = safeLower(answerText);
            String categoryLower = safeLower(categoryText);
            boolean imageAnswer = answerText.contains(IMAGE_PREFIX);

            Set<String> matchedTerms = new LinkedHashSet<>();
            double lexicalHit = 0.0;

            if (!queryLower.isBlank() && questionLower.equals(queryLower)) {
                lexicalHit += 16.0;
                matchedTerms.add(keyword);
            } else if (!queryLower.isBlank() && questionLower.startsWith(queryLower)) {
                lexicalHit += 9.0;
                matchedTerms.add(keyword);
            } else if (!queryLower.isBlank() && questionLower.contains(queryLower)) {
                lexicalHit += 6.0;
                matchedTerms.add(keyword);
            }

            for (String token : tokens) {
                String current = safeLower(token);
                if (current.isBlank()) {
                    continue;
                }
                if (questionLower.contains(current)) {
                    lexicalHit += 3.0;
                    matchedTerms.add(token);
                }
                if (categoryLower.contains(current)) {
                    lexicalHit += 2.0;
                    matchedTerms.add(token);
                }
                if (answerLower.contains(current)) {
                    lexicalHit += 1.2;
                    matchedTerms.add(token);
                }
            }

            double lexicalScore = lexicalHit <= 0
                    ? 0.0
                    : Math.min(100.0, lexicalHit * 100.0 / Math.max(4.0, tokens.size() * 3.0));
            double semanticScore = Math.max(0.0, semanticScoreMap.getOrDefault(item.getId(), 0.0)) * 100.0;
            double fuzzyScore = calculateFuzzyScore(keyword, item);

            if (lexicalScore <= 0.0 && semanticScore < 55.0 && fuzzyScore < 58.0) {
                continue;
            }

            double businessBoost = 0.0;
            double penalty = 0.0;

            if (medicalIntent) {
                if (containsAny(questionText, MEDICAL_HINTS)) {
                    businessBoost += 16.0;
                }
                if (containsAny(answerText, MEDICAL_HINTS)) {
                    businessBoost += 6.0;
                }
                if (containsNoticeWord(questionText) && !containsAny(questionText, MEDICAL_HINTS)) {
                    penalty += 18.0;
                }
            }
            if (contactIntent && (containsPhoneOrAddress(answerText) || containsPhoneOrAddress(questionText))) {
                businessBoost += 12.0;
            }
            if (visualIntent) {
                if (imageAnswer) {
                    businessBoost += 22.0;
                }
                if (containsAny(questionText, VISUAL_HINTS)) {
                    businessBoost += 12.0;
                }
                if (containsAny(answerText, VISUAL_HINTS)) {
                    businessBoost += 4.0;
                }
                if (containsNoticeWord(questionText) && !containsAny(questionText, VISUAL_HINTS)) {
                    penalty += 16.0;
                }
            }
            if (looksLikeScript(answerText)) {
                penalty += 40.0;
            }
            if (!imageAnswer && looksLikeNoise(answerText)) {
                penalty += 12.0;
            }
            if (containsNoticeWord(questionText) && tokens.size() <= 2) {
                penalty += 6.0;
            }
            if (!imageAnswer && answerText.length() > 1600) {
                penalty += 3.0;
            }

            double hotBoost = Math.min(item.getHitCount() == null ? 0.0 : item.getHitCount() * 0.05, 2.0);
            double finalScore = lexicalScore * 0.58 + semanticScore * 0.22 + fuzzyScore * 0.20 + businessBoost + hotBoost - penalty;
            if (finalScore <= 0.0) {
                continue;
            }

            SearchResultItem vo = new SearchResultItem();
            vo.setQuestionId(item.getId());
            vo.setQuestion(questionText);
            vo.setRawAnswer(cleanReferenceText(answerText, 1800));
            vo.setAnswer(previewAnswer
                    ? buildAnswerPreview(keyword, answerText, matchedTerms)
                    : cleanReferenceText(answerText, 1600));
            vo.setCategory(categoryText);
            vo.setMatchedTerms(new ArrayList<>(matchedTerms).subList(0, Math.min(5, matchedTerms.size())));
            vo.setScore(Math.min(100.0, finalScore));
            vo.setRetrievalSource(resolveRetrievalSource(lexicalScore, semanticScore, fuzzyScore));
            vo.setExplanation(buildExplanation(matchedTerms, lexicalScore, semanticScore, fuzzyScore, businessBoost, penalty));
            result.add(vo);
        }

        result.sort(Comparator.comparingDouble(SearchResultItem::getScore).reversed());
        return result;
    }

    private double calculateFuzzyScore(String keyword, Question item) {
        String query = normalizeComparableText(keyword);
        if (query.isEmpty()) {
            return 0.0;
        }
        String title = normalizeComparableText(item.getQuestion());
        String category = normalizeComparableText(item.getCategory());
        double titleScore = similarity(query, title);
        double categoryScore = similarity(query, category);
        return Math.max(titleScore, categoryScore) * 100.0;
    }

    private List<String> tokenize(String text) {
        List<String> words = jiebaSegmenter.sentenceProcess(text);
        Set<String> unique = new LinkedHashSet<>();
        for (String word : words) {
            String token = word == null ? "" : word.trim();
            if (token.length() >= 2) {
                unique.add(token);
            }
        }
        if (unique.isEmpty() && text.length() >= 2) {
            unique.add(text);
        }
        return new ArrayList<>(unique);
    }

    private List<String> expandTokens(String keyword, List<String> baseTokens) {
        LinkedHashSet<String> expanded = new LinkedHashSet<>();
        String raw = normalizeQuery(keyword);
        if (!raw.isBlank()) {
            expanded.add(raw);
        }
        for (String token : baseTokens) {
            String normalized = normalizeQuery(token);
            if (normalized.isBlank()) {
                continue;
            }
            expanded.add(normalized);
            addSynonyms(expanded, normalized);
            addNGrams(expanded, normalized);
        }
        return new ArrayList<>(expanded);
    }

    private void addSynonyms(Set<String> expanded, String token) {
        List<String> synonyms = SYNONYM_MAP.get(token);
        if (synonyms != null) {
            expanded.addAll(synonyms);
        }
        for (Map.Entry<String, List<String>> entry : SYNONYM_MAP.entrySet()) {
            if (entry.getValue().contains(token)) {
                expanded.add(entry.getKey());
                expanded.addAll(entry.getValue());
            }
        }
    }

    private static void addNGrams(Set<String> expanded, String token) {
        if (token.length() < 2) {
            return;
        }
        for (int i = 0; i < token.length() - 1; i++) {
            expanded.add(token.substring(i, i + 2));
        }
        if (token.length() <= 6) {
            for (int i = 0; i < token.length() - 2; i++) {
                expanded.add(token.substring(i, i + 3));
            }
        }
    }

    private static String buildAnswerPreview(String keyword, String answer, Set<String> matchedTerms) {
        String clean = cleanReferenceText(answer, 2400);
        if (!StringUtils.hasText(clean)) {
            return "该条知识暂未提取到可展示正文，请重新采集或手动补充。";
        }
        if (clean.contains(IMAGE_PREFIX)) {
            return clean;
        }

        List<String> sentences = splitSentences(clean);
        Set<String> preferredTokens = new LinkedHashSet<>();
        boolean contactIntent = containsAny(keyword, CONTACT_HINTS);
        if (StringUtils.hasText(keyword)) {
            preferredTokens.add(normalizeQuery(keyword));
        }
        for (String term : matchedTerms) {
            if (StringUtils.hasText(term)) {
                preferredTokens.add(normalizeQuery(term));
            }
        }
        if (contactIntent) {
            preferredTokens.addAll(CONTACT_HINTS);
        }

        List<SentenceScore> scored = new ArrayList<>();
        for (String sentence : sentences) {
            int score = scoreSentence(sentence, preferredTokens, contactIntent);
            if (score > 0) {
                scored.add(new SentenceScore(sentence, score));
            }
        }
        scored.sort(Comparator.comparingInt(SentenceScore::score).reversed());

        LinkedHashSet<String> selected = new LinkedHashSet<>();
        for (SentenceScore item : scored) {
            selected.add(item.text());
            if (selected.size() >= 2) {
                break;
            }
        }

        String preview = selected.isEmpty()
                ? extractAroundKeyword(clean, preferredTokens)
                : String.join("；", selected);

        preview = preview.trim();
        if (preview.length() > PREVIEW_MAX_LENGTH) {
            preview = preview.substring(0, PREVIEW_MAX_LENGTH) + "...";
        }
        if (!preview.endsWith("。") && !preview.endsWith("；") && !preview.endsWith("!") && !preview.endsWith("...")) {
            preview = preview + "。";
        }
        return preview;
    }

    private static List<String> splitSentences(String text) {
        List<String> result = new ArrayList<>();
        for (String item : text.split("[。！；;\\n\\r]+")) {
            String sentence = item == null ? "" : item.trim();
            if (sentence.length() >= 8 && !looksLikeNoise(sentence) && !looksLikeScript(sentence)) {
                result.add(sentence);
            }
        }
        return result;
    }

    private static int scoreSentence(String sentence, Set<String> tokens, boolean contactIntent) {
        int score = 0;
        for (String token : tokens) {
            if (StringUtils.hasText(token) && sentence.contains(token.trim())) {
                score += Math.max(2, token.trim().length());
            }
        }
        if (sentence.contains("地址") || sentence.contains("地点") || sentence.contains("电话")
                || sentence.contains("时间") || sentence.contains("开放") || sentence.contains("关闭")
                || sentence.contains("门诊") || sentence.contains("报销")) {
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

    private static String extractAroundKeyword(String text, Set<String> tokens) {
        for (String token : tokens) {
            if (!StringUtils.hasText(token)) {
                continue;
            }
            int index = text.indexOf(token.trim());
            if (index >= 0) {
                int start = Math.max(0, index - 45);
                int end = Math.min(text.length(), index + token.trim().length() + 85);
                return text.substring(start, end);
            }
        }
        return text.length() > 120 ? text.substring(0, 120) + "..." : text;
    }

    public static String cleanReferenceText(String text, int maxLength) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        if (text.contains(IMAGE_PREFIX)) {
            String value = CONTROL_PATTERN.matcher(text).replaceAll("").trim();
            return value.length() > maxLength ? value.substring(0, maxLength) : value;
        }

        String value = BROKEN_CHAR_PATTERN.matcher(text).replaceAll("");
        value = CONTROL_PATTERN.matcher(value).replaceAll("");
        value = SPACE_PATTERN.matcher(value).replaceAll(" ");
        value = value.replace('\u00A0', ' ');
        value = value.trim();

        List<String> kept = new ArrayList<>();
        for (String sentence : value.split("[。！；;\\n\\r]+")) {
            String item = sentence == null ? "" : sentence.trim();
            if (item.length() < 4 || looksLikeNoise(item) || looksLikeScript(item)) {
                continue;
            }
            kept.add(item);
        }
        String normalized = String.join("。", kept);
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }

    private static boolean looksLikeNoise(String sentence) {
        for (String marker : NOISE_MARKERS) {
            if (sentence.contains(marker)) {
                return true;
            }
        }
        int digitCount = 0;
        for (int i = 0; i < sentence.length(); i++) {
            if (Character.isDigit(sentence.charAt(i))) {
                digitCount++;
            }
        }
        return sentence.length() > 120 && digitCount > 18;
    }

    private static boolean looksLikeScript(String text) {
        if (!StringUtils.hasText(text) || text.contains(IMAGE_PREFIX)) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        int symbolCount = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '{' || ch == '}' || ch == ';' || ch == '=' || ch == '(' || ch == ')') {
                symbolCount++;
            }
        }
        for (String marker : SCRIPT_MARKERS) {
            if (lower.contains(marker.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return text.length() > 50 && symbolCount >= 8;
    }

    private static String normalizeQuery(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) {
            return "";
        }
        value = TYPO_MAP.getOrDefault(value, value);
        value = value.replaceAll("\\s+", "");
        return value;
    }

    private static boolean containsAny(String text, Collection<String> keywords) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsNoticeWord(String text) {
        return containsAny(text, NOTICE_WORDS);
    }

    private static boolean containsPhoneOrAddress(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        return text.contains("电话") || text.contains("地址") || text.contains("邮箱")
                || text.contains("联系") || text.matches(".*\\d{7,}.*");
    }

    private static String normalizeComparableText(String text) {
        String value = normalizeQuery(text);
        value = value.replaceAll("[^\\p{IsHan}A-Za-z0-9]", "");
        return value.toLowerCase(Locale.ROOT);
    }

    private static String defaultText(String text) {
        return text == null ? "" : text;
    }

    private static String safeLower(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }

    private static double similarity(String query, String target) {
        if (query.isEmpty() || target.isEmpty()) {
            return 0.0;
        }
        if (target.contains(query)) {
            return 1.0;
        }
        double dice = diceCoefficient(query, target);
        double edit = editSimilarity(query, target);
        return Math.max(dice, edit);
    }

    private static double diceCoefficient(String a, String b) {
        Set<String> aGrams = toBigrams(a);
        Set<String> bGrams = toBigrams(b);
        if (aGrams.isEmpty() || bGrams.isEmpty()) {
            return 0.0;
        }
        int overlap = 0;
        for (String gram : aGrams) {
            if (bGrams.contains(gram)) {
                overlap++;
            }
        }
        return (2.0 * overlap) / (aGrams.size() + bGrams.size());
    }

    private static Set<String> toBigrams(String text) {
        Set<String> grams = new HashSet<>();
        if (text.length() == 1) {
            grams.add(text);
            return grams;
        }
        for (int i = 0; i < text.length() - 1; i++) {
            grams.add(text.substring(i, i + 2));
        }
        return grams;
    }

    private static double editSimilarity(String a, String b) {
        int max = Math.max(a.length(), b.length());
        if (max == 0) {
            return 1.0;
        }
        if (Math.abs(a.length() - b.length()) > 4) {
            return 0.0;
        }
        int distance = levenshteinDistance(a, b);
        return Math.max(0.0, 1.0 - (distance * 1.0 / max));
    }

    private static int levenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }
        return dp[a.length()][b.length()];
    }

    private static String resolveRetrievalSource(double lexicalScore, double semanticScore, double fuzzyScore) {
        if (lexicalScore > 0 && semanticScore >= 55.0) {
            return "hybrid";
        }
        if (fuzzyScore >= 58.0 && lexicalScore <= 0) {
            return "fuzzy";
        }
        if (semanticScore >= 55.0) {
            return "semantic";
        }
        return "keyword";
    }

    private static String buildExplanation(Set<String> matchedTerms,
                                           double lexicalScore,
                                           double semanticScore,
                                           double fuzzyScore,
                                           double businessBoost,
                                           double penalty) {
        List<String> reasons = new ArrayList<>();
        if (!matchedTerms.isEmpty()) {
            reasons.add("命中关键词：" + String.join("、", matchedTerms));
        }
        if (lexicalScore > 0) {
            reasons.add("关键词匹配 " + Math.round(lexicalScore) + "%");
        }
        if (semanticScore >= 55.0) {
            reasons.add("语义召回 " + Math.round(semanticScore) + "%");
        }
        if (fuzzyScore >= 58.0 && lexicalScore <= 0) {
            reasons.add("模糊匹配 " + Math.round(fuzzyScore) + "%");
        }
        if (businessBoost > 0) {
            reasons.add("业务规则加权");
        }
        if (penalty > 0) {
            reasons.add("已过滤公告或脚本噪声");
        }
        if (reasons.isEmpty()) {
            reasons.add("根据综合排序返回");
        }
        return String.join("；", reasons);
    }

    private record SentenceScore(String text, int score) {
    }
}
