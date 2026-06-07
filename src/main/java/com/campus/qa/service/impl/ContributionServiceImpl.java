package com.campus.qa.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.campus.qa.dto.ContributionSubmitRequest;
import com.campus.qa.entity.Contribution;
import com.campus.qa.entity.Question;
import com.campus.qa.mapper.ContributionMapper;
import com.campus.qa.mapper.QuestionMapper;
import com.campus.qa.service.ContributionService;
import com.campus.qa.service.QuestionEmbeddingService;
import com.campus.qa.service.SensitiveWordService;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ContributionServiceImpl implements ContributionService {

    private final ContributionMapper contributionMapper;
    private final QuestionMapper questionMapper;
    private final RedisTemplate<Object, Object> redisTemplate;
    private final SensitiveWordService sensitiveWordService;
    private final QuestionEmbeddingService questionEmbeddingService;

    public ContributionServiceImpl(ContributionMapper contributionMapper,
                                   QuestionMapper questionMapper,
                                   RedisTemplate<Object, Object> redisTemplate,
                                   SensitiveWordService sensitiveWordService,
                                   QuestionEmbeddingService questionEmbeddingService) {
        this.contributionMapper = contributionMapper;
        this.questionMapper = questionMapper;
        this.redisTemplate = redisTemplate;
        this.sensitiveWordService = sensitiveWordService;
        this.questionEmbeddingService = questionEmbeddingService;
    }

    @Override
    public Contribution submit(ContributionSubmitRequest request, String userIp) {
        String question = trimOrEmpty(request.getQuestion());
        String answer = trimOrEmpty(request.getAnswer());
        String category = trimOrEmpty(request.getCategory());
        String ip = StringUtils.hasText(userIp) ? userIp : "unknown";

        validateLength(question, 5, 200, "question length must be 5-200");
        validateLength(answer, 10, 2000, "answer length must be 10-2000");

        checkDailyIpLimit(ip);
        checkDuplicate(question);

        Contribution contribution = new Contribution();
        contribution.setQuestion(question);
        contribution.setAnswer(answer);
        contribution.setCategory(category);
        contribution.setSubmitIp(ip);
        contribution.setSubmitTime(LocalDateTime.now());

        SensitiveWordService.ModerationHit hitQuestion = sensitiveWordService.match(question);
        SensitiveWordService.ModerationHit hitAnswer = sensitiveWordService.match(answer);
        SensitiveWordService.ModerationHit hit = pickStrongerHit(hitQuestion, hitAnswer);
        if (hit.hit()) {
            if ("high".equals(hit.level())) {
                contribution.setStatus("rejected");
                contribution.setAuditTime(LocalDateTime.now());
                contribution.setRejectReason("auto reject: sensitive word=" + hit.word());
                contributionMapper.insert(contribution);
                return contribution;
            }
            if ("medium".equals(hit.level())) {
                contribution.setStatus("pending");
                contribution.setRejectReason("pending review: sensitive word=" + hit.word());
                contributionMapper.insert(contribution);
                return contribution;
            }
        }

        if (isHighConfidence(question, answer, category)) {
            contribution.setStatus("approved");
            contribution.setAuditTime(LocalDateTime.now());
            contribution.setRejectReason(null);
            contributionMapper.insert(contribution);
            insertQuestion(question, answer, category, "contribution-auto");
            return contribution;
        }

        contribution.setStatus("pending");
        contributionMapper.insert(contribution);
        return contribution;
    }

    @Override
    public List<Contribution> mine(String userIp) {
        String ip = StringUtils.hasText(userIp) ? userIp : "unknown";
        QueryWrapper<Contribution> wrapper = new QueryWrapper<>();
        wrapper.eq("submit_ip", ip).orderByDesc("id");
        return contributionMapper.selectList(wrapper);
    }

    private void checkDailyIpLimit(String ip) {
        String date = LocalDate.now().toString();
        String key = "contribution:limit:" + ip + ":" + date;
        Long current = redisTemplate.opsForValue().increment(key);
        if (current != null && current == 1L) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime end = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);
            redisTemplate.expire(key, Duration.between(now, end));
        }
        if (current != null && current > 5) {
            throw new IllegalArgumentException("daily limit exceeded: max 5 contributions per IP");
        }
    }

    private void checkDuplicate(String question) {
        QueryWrapper<Question> q1 = new QueryWrapper<>();
        q1.eq("question", question);
        if (questionMapper.selectCount(q1) > 0) {
            throw new IllegalArgumentException("question already exists in knowledge base");
        }

        QueryWrapper<Contribution> q2 = new QueryWrapper<>();
        q2.eq("question", question).in("status", List.of("pending", "approved"));
        if (contributionMapper.selectCount(q2) > 0) {
            throw new IllegalArgumentException("question already exists in pending/approved contributions");
        }
    }

    private static boolean isHighConfidence(String question, String answer, String category) {
        if (!StringUtils.hasText(category)) {
            return false;
        }
        if (question.length() < 8 || answer.length() < 30) {
            return false;
        }
        return question.contains("\uFF1F") || question.contains("?");
    }

    private static SensitiveWordService.ModerationHit pickStrongerHit(
            SensitiveWordService.ModerationHit left,
            SensitiveWordService.ModerationHit right) {
        if (!left.hit()) {
            return right;
        }
        if (!right.hit()) {
            return left;
        }
        return levelRank(left.level()) >= levelRank(right.level()) ? left : right;
    }

    private static int levelRank(String level) {
        if ("high".equals(level)) {
            return 3;
        }
        if ("medium".equals(level)) {
            return 2;
        }
        if ("low".equals(level)) {
            return 1;
        }
        return 0;
    }

    private void insertQuestion(String question, String answer, String category, String source) {
        Question item = new Question();
        item.setQuestion(question);
        item.setAnswer(answer);
        item.setCategory(category);
        item.setSource(source);
        item.setHitCount(0L);
        item.setCreateTime(LocalDateTime.now());
        questionMapper.insert(item);
        questionEmbeddingService.upsertEmbedding(item);
    }

    private static void validateLength(String text, int min, int max, String msg) {
        int len = trimOrEmpty(text).length();
        if (len < min || len > max) {
            throw new IllegalArgumentException(msg);
        }
    }

    private static String trimOrEmpty(String text) {
        return text == null ? "" : text.trim();
    }
}
