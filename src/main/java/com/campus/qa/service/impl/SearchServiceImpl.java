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
import java.util.stream.Collectors;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class SearchServiceImpl implements SearchService {

    private static final Map<String, List<String>> SYNONYM_MAP = Map.of(
            "\u98df\u5802", List.of("\u9910\u5385", "\u996d\u5802"),
            "\u56fe\u4e66\u9986", List.of("\u81ea\u4e60\u5ba4", "\u9605\u89c8\u5ba4"),
            "\u6253\u5370", List.of("\u6587\u5370", "\u590d\u5370"),
            "\u6821\u8f66", List.of("\u73ed\u8f66", "\u901a\u52e4\u8f66"),
            "\u5bbf\u820d", List.of("\u516c\u5bd3", "\u5bdd\u5ba4"),
            "\u62a5\u4fee", List.of("\u7ef4\u4fee", "\u4fee\u7406")
    );

    private final QuestionMapper questionMapper;
    private final QueryLogAsyncService queryLogAsyncService;
    private final CacheStatsService cacheStatsService;
    private final RedisTemplate<Object, Object> redisTemplate;
    private final QuestionEmbeddingService questionEmbeddingService;
    private final EmbeddingProperties embeddingProperties;
    private final JiebaSegmenter jiebaSegmenter = new JiebaSegmenter();

    public SearchServiceImpl(QuestionMapper questionMapper,
                             QueryLogAsyncService queryLogAsyncService,
                             CacheStatsService cacheStatsService,
                             RedisTemplate<Object, Object> redisTemplate,
                             QuestionEmbeddingService questionEmbeddingService,
                             EmbeddingProperties embeddingProperties) {
        this.questionMapper = questionMapper;
        this.queryLogAsyncService = queryLogAsyncService;
        this.cacheStatsService = cacheStatsService;
        this.redisTemplate = redisTemplate;
        this.questionEmbeddingService = questionEmbeddingService;
        this.embeddingProperties = embeddingProperties;
    }

    @Override
    public List<SearchResultItem> search(String keyword, String userIp) {
        String q = keyword == null ? "" : keyword.trim();
        if (q.isEmpty()) {
            return List.of();
        }
        cacheStatsService.incTotal();

        String cacheKey = "search:" + q;
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached instanceof List<?>) {
                @SuppressWarnings("unchecked")
                List<SearchResultItem> result = (List<SearchResultItem>) cached;
                cacheStatsService.incHit();
                Long topId = result.isEmpty() ? null : result.get(0).getQuestionId();
                queryLogAsyncService.saveLogAsync(q, topId, userIp);
                return result;
            }
        } catch (Exception ignore) {
            // Ignore cache read failures.
        }

        List<String> segmentedTokens = tokenize(q);
        List<String> queryTokens = expandTokens(q, segmentedTokens.isEmpty() ? List.of(q) : segmentedTokens);
        Map<Long, Double> semanticScoreMap = searchSemanticScores(q);

        List<Question> keywordCandidates = findKeywordCandidates(queryTokens);
        Map<Long, Question> candidateMap = new LinkedHashMap<>();
        for (Question question : keywordCandidates) {
            candidateMap.put(question.getId(), question);
        }
        if (!semanticScoreMap.isEmpty()) {
            candidateMap.putAll(loadQuestionsByIds(semanticScoreMap.keySet()));
        }

        List<SearchResultItem> ranked = rank(new ArrayList<>(candidateMap.values()), q, queryTokens, semanticScoreMap);
        List<SearchResultItem> top5 = ranked.size() > 5 ? new ArrayList<>(ranked.subList(0, 5)) : ranked;

        try {
            redisTemplate.opsForValue().set(cacheKey, top5, Duration.ofMinutes(5));
        } catch (Exception ignore) {
            // Ignore cache write failures.
        }
        Long topId = top5.isEmpty() ? null : top5.get(0).getQuestionId();
        queryLogAsyncService.saveLogAsync(q, topId, userIp);
        return top5;
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
                                        Map<Long, Double> semanticScoreMap) {
        List<SearchResultItem> result = new ArrayList<>();
        String rawQuery = safeLower(keyword);

        for (Question item : candidates) {
            String questionText = safeLower(item.getQuestion());
            String answerText = safeLower(item.getAnswer());
            String categoryText = safeLower(item.getCategory());
            Set<String> matchedTerms = new LinkedHashSet<>();
            double lexicalHit = 0.0;

            if (!rawQuery.isBlank() && questionText.equals(rawQuery)) {
                lexicalHit += 10.0;
                matchedTerms.add(keyword);
            } else if (!rawQuery.isBlank() && questionText.contains(rawQuery)) {
                lexicalHit += 5.0;
                matchedTerms.add(keyword);
            }

            for (String token : tokens) {
                String t = safeLower(token);
                if (questionText.contains(t)) {
                    lexicalHit += 2.5;
                    matchedTerms.add(token);
                }
                if (answerText.contains(t)) {
                    lexicalHit += 1.0;
                    matchedTerms.add(token);
                }
                if (categoryText.contains(t)) {
                    lexicalHit += 1.5;
                    matchedTerms.add(token);
                }
            }

            double lexicalScore = lexicalHit <= 0
                    ? 0.0
                    : Math.min(100.0, lexicalHit * 100.0 / Math.max(2.0, tokens.size() * 2.5));
            double semanticScore = Math.max(0.0, semanticScoreMap.getOrDefault(item.getId(), 0.0)) * 100.0;

            if (lexicalScore <= 0.0 && semanticScore < 55.0) {
                continue;
            }

            double hotBoost = Math.min(item.getHitCount() == null ? 0.0 : item.getHitCount() * 0.05, 2.0);
            double finalScore = lexicalScore * 0.7 + semanticScore * 0.3 + hotBoost;

            SearchResultItem vo = new SearchResultItem();
            vo.setQuestionId(item.getId());
            vo.setQuestion(item.getQuestion());
            vo.setAnswer(item.getAnswer());
            vo.setCategory(item.getCategory());
            vo.setMatchedTerms(new ArrayList<>(matchedTerms));
            vo.setScore(Math.min(100.0, finalScore));
            result.add(vo);
        }

        result.sort(Comparator.comparingDouble(SearchResultItem::getScore).reversed());
        return result;
    }

    private List<String> tokenize(String text) {
        List<String> words = jiebaSegmenter.sentenceProcess(text);
        Set<String> unique = new HashSet<>();
        for (String word : words) {
            String token = word == null ? "" : word.trim();
            if (token.length() >= 2) {
                unique.add(token);
            }
        }
        return new ArrayList<>(unique);
    }

    private List<String> expandTokens(String keyword, List<String> baseTokens) {
        LinkedHashSet<String> expanded = new LinkedHashSet<>();
        String raw = keyword == null ? "" : keyword.trim();
        if (!raw.isBlank()) {
            expanded.add(raw);
        }
        for (String token : baseTokens) {
            String normalized = token == null ? "" : token.trim();
            if (normalized.isBlank()) {
                continue;
            }
            expanded.add(normalized);
            List<String> synonyms = SYNONYM_MAP.get(normalized);
            if (synonyms != null) {
                expanded.addAll(synonyms);
            }
        }
        return new ArrayList<>(expanded);
    }

    private static String safeLower(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }
}
