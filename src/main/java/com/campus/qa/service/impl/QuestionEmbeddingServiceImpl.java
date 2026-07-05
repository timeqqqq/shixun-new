package com.campus.qa.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.qa.config.EmbeddingProperties;
import com.campus.qa.entity.Question;
import com.campus.qa.entity.QuestionEmbedding;
import com.campus.qa.mapper.QuestionEmbeddingMapper;
import com.campus.qa.mapper.QuestionMapper;
import com.campus.qa.service.EmbeddingProvider;
import com.campus.qa.service.QuestionEmbeddingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class QuestionEmbeddingServiceImpl implements QuestionEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(QuestionEmbeddingServiceImpl.class);

    private final QuestionEmbeddingMapper questionEmbeddingMapper;
    private final QuestionMapper questionMapper;
    private final EmbeddingProvider embeddingProvider;
    private final EmbeddingProperties embeddingProperties;
    private final ObjectMapper objectMapper;
    private final ExecutorService rebuildExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "embedding-rebuild");
        thread.setDaemon(true);
        return thread;
    });

    private final AtomicBoolean rebuildRunning = new AtomicBoolean(false);
    private final AtomicInteger rebuildTotal = new AtomicInteger(0);
    private final AtomicInteger rebuildProcessed = new AtomicInteger(0);
    private final AtomicInteger rebuildSuccess = new AtomicInteger(0);
    private final AtomicInteger rebuildFail = new AtomicInteger(0);
    private volatile String rebuildMessage = "未开始";

    public QuestionEmbeddingServiceImpl(QuestionEmbeddingMapper questionEmbeddingMapper,
                                        QuestionMapper questionMapper,
                                        EmbeddingProvider embeddingProvider,
                                        EmbeddingProperties embeddingProperties,
                                        ObjectMapper objectMapper) {
        this.questionEmbeddingMapper = questionEmbeddingMapper;
        this.questionMapper = questionMapper;
        this.embeddingProvider = embeddingProvider;
        this.embeddingProperties = embeddingProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void upsertEmbedding(Question question) {
        upsertEmbeddingInternal(question);
    }

    @Override
    public List<SemanticSearchHit> searchSimilar(String query, int topN) {
        if (!isEnabled() || !StringUtils.hasText(query)) {
            return List.of();
        }
        try {
            List<Double> queryVector = embeddingProvider.embed(query.trim());
            if (queryVector.isEmpty()) {
                return List.of();
            }

            List<QuestionEmbedding> all = questionEmbeddingMapper.selectList(new LambdaQueryWrapper<>());
            Map<Long, Double> bestScoreByQuestion = new java.util.HashMap<>();
            for (QuestionEmbedding item : all) {
                List<Double> itemVector = parseVector(item.getVectorJson());
                if (itemVector.isEmpty() || itemVector.size() != queryVector.size()) {
                    continue;
                }
                double similarity = cosineSimilarity(queryVector, itemVector);
                if (similarity > 0 && item.getQuestionId() != null) {
                    bestScoreByQuestion.merge(item.getQuestionId(), similarity, Math::max);
                }
            }
            List<SemanticSearchHit> hits = bestScoreByQuestion.entrySet().stream()
                    .map(entry -> new SemanticSearchHit(entry.getKey(), entry.getValue()))
                    .collect(Collectors.toCollection(ArrayList::new));
            hits.sort(Comparator.comparingDouble(SemanticSearchHit::score).reversed());
            return hits.size() > topN ? new ArrayList<>(hits.subList(0, topN)) : hits;
        } catch (Exception ex) {
            log.warn("semantic search degraded to keyword mode: {}", ex.getMessage());
            return List.of();
        }
    }

    @Override
    public int rebuildAll() {
        if (!isEnabled()) {
            rebuildMessage = "向量服务未启用";
            return 0;
        }
        List<Question> allQuestions = questionMapper.selectList(new LambdaQueryWrapper<>());
        resetRebuildState(allQuestions.size());

        for (Question question : allQuestions) {
            rebuildProcessed.incrementAndGet();
            if (upsertEmbeddingInternal(question)) {
                rebuildSuccess.incrementAndGet();
            } else {
                rebuildFail.incrementAndGet();
            }
        }
        rebuildMessage = "重建完成";
        return allQuestions.size();
    }

    @Override
    public RebuildStatus startRebuildAsync() {
        if (!isEnabled()) {
            return new RebuildStatus(false, false, 0, 0, 0, 0, "向量服务未启用或未配置 API Key");
        }
        if (!rebuildRunning.compareAndSet(false, true)) {
            return rebuildStatus();
        }

        List<Question> allQuestions = questionMapper.selectList(new LambdaQueryWrapper<>());
        resetRebuildState(allQuestions.size());
        rebuildMessage = "后台重建中";

        rebuildExecutor.submit(() -> {
            try {
                for (Question question : allQuestions) {
                    rebuildProcessed.incrementAndGet();
                    if (upsertEmbeddingInternal(question)) {
                        rebuildSuccess.incrementAndGet();
                    } else {
                        rebuildFail.incrementAndGet();
                    }
                }
                rebuildMessage = "重建完成";
            } catch (Exception ex) {
                rebuildMessage = "重建异常: " + ex.getMessage();
                log.error("embedding rebuild failed", ex);
            } finally {
                rebuildRunning.set(false);
            }
        });
        return rebuildStatus();
    }

    @Override
    public RebuildStatus rebuildStatus() {
        return new RebuildStatus(
                isEnabled(),
                rebuildRunning.get(),
                rebuildTotal.get(),
                rebuildProcessed.get(),
                rebuildSuccess.get(),
                rebuildFail.get(),
                rebuildMessage
        );
    }

    @Override
    public long countEmbeddedQuestions() {
        List<QuestionEmbedding> all = questionEmbeddingMapper.selectList(new LambdaQueryWrapper<>());
        Set<Long> validQuestionIds = questionMapper.selectList(new LambdaQueryWrapper<Question>().select(Question::getId))
                .stream()
                .map(Question::getId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        if (validQuestionIds.isEmpty()) {
            return 0;
        }
        return all.stream()
                .map(QuestionEmbedding::getQuestionId)
                .filter(validQuestionIds::contains)
                .distinct()
                .count();
    }

    @Override
    public boolean isEnabled() {
        return embeddingProvider.isAvailable();
    }

    public int candidateLimit() {
        return embeddingProperties.getCandidateLimit();
    }

    public int semanticTopN() {
        return embeddingProperties.getSemanticTopN();
    }

    private boolean upsertEmbeddingInternal(Question question) {
        if (!isEnabled() || question == null || question.getId() == null) {
            return false;
        }
        String content = buildEmbeddingText(question);
        String contentHash = sha256(content);
        QuestionEmbedding existing = questionEmbeddingMapper.selectOne(new LambdaQueryWrapper<QuestionEmbedding>()
                .eq(QuestionEmbedding::getQuestionId, question.getId()));
        if (existing != null && contentHash.equals(existing.getContentHash())) {
            return true;
        }

        try {
            List<Double> vector = embeddingProvider.embed(content);
            if (vector.isEmpty()) {
                return false;
            }

            QuestionEmbedding target = existing == null ? new QuestionEmbedding() : existing;
            target.setQuestionId(question.getId());
            target.setEmbeddingModel(embeddingProvider.modelName());
            target.setVectorJson(objectMapper.writeValueAsString(vector));
            target.setContentHash(contentHash);
            if (existing == null) {
                target.setCreateTime(LocalDateTime.now());
                questionEmbeddingMapper.insert(target);
            } else {
                questionEmbeddingMapper.updateById(target);
            }
            return true;
        } catch (Exception ex) {
            log.warn("failed to build embedding for questionId={}: {}", question.getId(), ex.getMessage());
            return false;
        }
    }

    private void resetRebuildState(int total) {
        rebuildTotal.set(total);
        rebuildProcessed.set(0);
        rebuildSuccess.set(0);
        rebuildFail.set(0);
    }

    private List<Double> parseVector(String vectorJson) throws JsonProcessingException {
        if (!StringUtils.hasText(vectorJson)) {
            return List.of();
        }
        return objectMapper.readValue(vectorJson, new TypeReference<List<Double>>() {
        });
    }

    private static String buildEmbeddingText(Question question) {
        return "question: " + safe(question.getQuestion())
                + "\ncategory: " + safe(question.getCategory())
                + "\nanswer: " + safe(question.getAnswer());
    }

    private static String safe(String text) {
        return text == null ? "" : text.trim();
    }

    private static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception ex) {
            throw new IllegalStateException("sha-256 unavailable", ex);
        }
    }

    private static double cosineSimilarity(List<Double> left, List<Double> right) {
        double dot = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (int i = 0; i < left.size(); i++) {
            double a = left.get(i);
            double b = right.get(i);
            dot += a * b;
            leftNorm += a * a;
            rightNorm += b * b;
        }
        if (leftNorm <= 0 || rightNorm <= 0) {
            return 0.0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}
