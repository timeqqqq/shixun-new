package com.campus.qa.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.campus.qa.entity.Question;
import com.campus.qa.mapper.QueryLogMapper;
import com.campus.qa.mapper.QuestionMapper;
import com.campus.qa.service.HotService;
import com.campus.qa.vo.HotQuestionItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class HotServiceImpl implements HotService {

    private static final int HOT_LIMIT = 10;
    private static final String KEY_PREFIX = "hot:";

    private final QueryLogMapper queryLogMapper;
    private final QuestionMapper questionMapper;
    private final RedisTemplate<Object, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public HotServiceImpl(QueryLogMapper queryLogMapper,
                          QuestionMapper questionMapper,
                          RedisTemplate<Object, Object> redisTemplate,
                          ObjectMapper objectMapper) {
        this.queryLogMapper = queryLogMapper;
        this.questionMapper = questionMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<HotQuestionItem> listHot(String period) {
        String p = normalizePeriod(period);
        String key = KEY_PREFIX + p;

        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached instanceof List<?>) {
                List<HotQuestionItem> list = new ArrayList<>();
                for (Object item : (List<?>) cached) {
                    if (item instanceof HotQuestionItem hotQuestionItem) {
                        list.add(hotQuestionItem);
                    } else {
                        list.add(objectMapper.convertValue(item, HotQuestionItem.class));
                    }
                }
                return list;
            }
        } catch (Exception ignore) {
            // Ignore cache read failures.
        }

        List<HotQuestionItem> fresh = computeHot(p);
        try {
            redisTemplate.opsForValue().set(key, fresh, Duration.ofHours(1));
        } catch (Exception ignore) {
            // Ignore cache write failures.
        }
        return fresh;
    }

    @Override
    public void pinQuestion(Long questionId) {
        Question question = questionMapper.selectById(questionId);
        if (question == null) {
            throw new IllegalArgumentException("question not found: " + questionId);
        }

        QueryWrapper<Question> maxWrapper = new QueryWrapper<>();
        maxWrapper.eq("is_pinned", 1).orderByDesc("pinned_order").last("LIMIT 1");
        Question top = questionMapper.selectOne(maxWrapper);
        int next = top == null || top.getPinnedOrder() == null ? 1 : top.getPinnedOrder() + 1;

        question.setIsPinned(1);
        question.setPinnedOrder(next);
        questionMapper.updateById(question);

        refreshAllCache();
    }

    @Override
    public void refreshAllCache() {
        for (String p : List.of("week", "month", "all")) {
            List<HotQuestionItem> list = computeHot(p);
            try {
                redisTemplate.opsForValue().set(KEY_PREFIX + p, list, Duration.ofHours(1));
            } catch (Exception ignore) {
                // Ignore cache write failures.
            }
        }
    }

    @Scheduled(cron = "0 0 * * * ?")
    public void scheduledRefresh() {
        refreshAllCache();
    }

    private List<HotQuestionItem> computeHot(String period) {
        LocalDateTime start = switch (period) {
            case "week" -> LocalDateTime.now().minusDays(7);
            case "month" -> LocalDateTime.now().minusDays(30);
            default -> null;
        };

        List<Map<String, Object>> stats = queryLogMapper.countHotQuestions(start, HOT_LIMIT * 2);
        Map<Long, Long> countMap = new HashMap<>();
        for (Map<String, Object> row : stats) {
            Number qid = (Number) row.get("questionId");
            Number cnt = (Number) row.get("cnt");
            if (qid != null && cnt != null) {
                countMap.put(qid.longValue(), cnt.longValue());
            }
        }

        List<Question> pinned = questionMapper.selectList(new QueryWrapper<Question>()
                .eq("is_pinned", 1)
                .orderByAsc("pinned_order")
                .last("LIMIT 5"));

        List<HotQuestionItem> result = new ArrayList<>();
        for (Question question : pinned) {
            HotQuestionItem item = toItem(question, countMap.getOrDefault(question.getId(), 0L), true);
            result.add(item);
        }

        if (!countMap.isEmpty()) {
            List<Question> hotQuestions = questionMapper.selectBatchIds(countMap.keySet());
            hotQuestions.sort(Comparator.comparingLong((Question question) ->
                    countMap.getOrDefault(question.getId(), 0L)).reversed());
            for (Question question : hotQuestions) {
                if (result.stream().anyMatch(x -> x.getQuestionId().equals(question.getId()))) {
                    continue;
                }
                result.add(toItem(question, countMap.getOrDefault(question.getId(), 0L), false));
                if (result.size() >= HOT_LIMIT) {
                    break;
                }
            }
        }

        return result.size() > HOT_LIMIT ? result.subList(0, HOT_LIMIT) : result;
    }

    private static HotQuestionItem toItem(Question question, Long count, boolean pinned) {
        HotQuestionItem item = new HotQuestionItem();
        item.setQuestionId(question.getId());
        item.setQuestion(question.getQuestion());
        item.setCategory(question.getCategory());
        item.setQueryCount(count);
        item.setPinned(pinned);
        return item;
    }

    private static String normalizePeriod(String period) {
        if (period == null) {
            return "week";
        }
        String p = period.trim().toLowerCase();
        return switch (p) {
            case "week", "month", "all" -> p;
            default -> "week";
        };
    }
}
