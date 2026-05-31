package com.campus.qa.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.campus.qa.entity.Question;
import com.campus.qa.mapper.QuestionMapper;
import com.campus.qa.service.QueryLogAsyncService;
import com.campus.qa.service.SearchService;
import com.campus.qa.service.stats.CacheStatsService;
import com.campus.qa.vo.SearchResultItem;
import com.huaban.analysis.jieba.JiebaSegmenter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class SearchServiceImpl implements SearchService {

    private final QuestionMapper questionMapper;
    private final QueryLogAsyncService queryLogAsyncService;
    private final CacheStatsService cacheStatsService;
    private final RedisTemplate<Object, Object> redisTemplate;
    private final JiebaSegmenter jiebaSegmenter = new JiebaSegmenter();

    public SearchServiceImpl(QuestionMapper questionMapper,
                             QueryLogAsyncService queryLogAsyncService,
                             CacheStatsService cacheStatsService,
                             RedisTemplate<Object, Object> redisTemplate) {
        this.questionMapper = questionMapper;
        this.queryLogAsyncService = queryLogAsyncService;
        this.cacheStatsService = cacheStatsService;
        this.redisTemplate = redisTemplate;
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
            // Cache read failure should not break search.
        }

        List<String> tokens = tokenize(q);
        final List<String> queryTokens = tokens.isEmpty() ? List.of(q) : tokens;

        QueryWrapper<Question> wrapper = new QueryWrapper<>();
        wrapper.and(w -> {
            for (int i = 0; i < queryTokens.size(); i++) {
                String token = queryTokens.get(i);
                if (i > 0) {
                    w.or();
                }
                w.like("question", token).or().like("answer", token);
            }
        });
        wrapper.last("LIMIT 100");

        List<Question> candidates = questionMapper.selectList(wrapper);
        List<SearchResultItem> ranked = rank(candidates, queryTokens);
        List<SearchResultItem> top5 = ranked.size() > 5 ? ranked.subList(0, 5) : ranked;

        try {
            redisTemplate.opsForValue().set(cacheKey, top5, Duration.ofMinutes(5));
        } catch (Exception ignore) {
            // Cache write failure should not break search.
        }
        Long topId = top5.isEmpty() ? null : top5.get(0).getQuestionId();
        queryLogAsyncService.saveLogAsync(q, topId, userIp);
        return top5;
    }

    private List<SearchResultItem> rank(List<Question> candidates, List<String> tokens) {
        List<SearchResultItem> result = new ArrayList<>();
        for (Question item : candidates) {
            String questionText = safeLower(item.getQuestion());
            String answerText = safeLower(item.getAnswer());
            int hit = 0;
            for (String token : tokens) {
                String t = token.toLowerCase(Locale.ROOT);
                if (questionText.contains(t)) {
                    hit += 2;
                }
                if (answerText.contains(t)) {
                    hit += 1;
                }
            }
            if (hit <= 0) {
                continue;
            }
            SearchResultItem vo = new SearchResultItem();
            vo.setQuestionId(item.getId());
            vo.setQuestion(item.getQuestion());
            vo.setAnswer(item.getAnswer());
            vo.setCategory(item.getCategory());
            vo.setScore(Math.min(100.0, hit * 100.0 / (tokens.size() * 2.0)));
            result.add(vo);
        }
        result.sort(Comparator.comparingDouble(SearchResultItem::getScore).reversed());
        return result;
    }

    private List<String> tokenize(String text) {
        List<String> words = jiebaSegmenter.sentenceProcess(text);
        Set<String> unique = new HashSet<>();
        for (String w : words) {
            String x = w == null ? "" : w.trim();
            if (x.length() >= 2) {
                unique.add(x);
            }
        }
        return new ArrayList<>(unique);
    }

    private static String safeLower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
