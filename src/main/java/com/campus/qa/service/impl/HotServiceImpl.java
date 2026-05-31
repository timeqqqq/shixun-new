package com.campus.qa.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.campus.qa.entity.Question;
import com.campus.qa.mapper.QueryLogMapper;
import com.campus.qa.mapper.QuestionMapper;
import com.campus.qa.service.HotService;
import com.campus.qa.vo.HotQuestionItem;
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

    public HotServiceImpl(QueryLogMapper queryLogMapper,
                          QuestionMapper questionMapper,
                          RedisTemplate<Object, Object> redisTemplate) {
        this.queryLogMapper = queryLogMapper;
        this.questionMapper = questionMapper;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public List<HotQuestionItem> listHot(String period) {
        String p = normalizePeriod(period);
        String key = KEY_PREFIX + p;

        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached instanceof List<?>) {
                @SuppressWarnings("unchecked")
                List<HotQuestionItem> list = (List<HotQuestionItem>) cached;
                return list;
            }
        } catch (Exception ignore) {
        }

        List<HotQuestionItem> fresh = computeHot(p);
        try {
            redisTemplate.opsForValue().set(key, fresh, Duration.ofHours(1));
        } catch (Exception ignore) {
        }
        return fresh;
    }

    @Override
    public void pinQuestion(Long questionId) {
        Question q = questionMapper.selectById(questionId);
        if (q == null) {
            throw new IllegalArgumentException("闂傚倸鍊搁崐鎼佸磻閸℃稑闂柨婵嗩槶閳ь剙鍊块幊鐐哄Ψ瑜夐弸鏍倵楠炲灝鍔氶柟铏姍楠炴鎮╃紒妯煎幈? " + questionId);
        }

        QueryWrapper<Question> maxWrapper = new QueryWrapper<>();
        maxWrapper.eq("is_pinned", 1).orderByDesc("pinned_order").last("LIMIT 1");
        Question top = questionMapper.selectOne(maxWrapper);
        int next = top == null || top.getPinnedOrder() == null ? 1 : top.getPinnedOrder() + 1;

        q.setIsPinned(1);
        q.setPinnedOrder(next);
        questionMapper.updateById(q);

        refreshAllCache();
    }

    @Override
    public void refreshAllCache() {
        for (String p : List.of("week", "month", "all")) {
            List<HotQuestionItem> list = computeHot(p);
            try {
                redisTemplate.opsForValue().set(KEY_PREFIX + p, list, Duration.ofHours(1));
            } catch (Exception ignore) {
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
        for (Question q : pinned) {
            HotQuestionItem item = toItem(q, countMap.getOrDefault(q.getId(), 0L), true);
            result.add(item);
        }

        if (!countMap.isEmpty()) {
            List<Question> hotQuestions = questionMapper.selectBatchIds(countMap.keySet());
            hotQuestions.sort(Comparator.comparingLong((Question q) -> countMap.getOrDefault(q.getId(), 0L)).reversed());
            for (Question q : hotQuestions) {
                if (result.stream().anyMatch(x -> x.getQuestionId().equals(q.getId()))) {
                    continue;
                }
                result.add(toItem(q, countMap.getOrDefault(q.getId(), 0L), false));
                if (result.size() >= HOT_LIMIT) {
                    break;
                }
            }
        }

        return result.size() > HOT_LIMIT ? result.subList(0, HOT_LIMIT) : result;
    }

    private static HotQuestionItem toItem(Question q, Long cnt, boolean pinned) {
        HotQuestionItem item = new HotQuestionItem();
        item.setQuestionId(q.getId());
        item.setQuestion(q.getQuestion());
        item.setCategory(q.getCategory());
        item.setQueryCount(cnt);
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
