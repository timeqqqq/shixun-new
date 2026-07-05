package com.campus.qa.service;

import com.campus.qa.entity.Question;
import java.util.List;

public interface QuestionEmbeddingService {

    void upsertEmbedding(Question question);

    List<SemanticSearchHit> searchSimilar(String query, int topN);

    int rebuildAll();

    RebuildStatus startRebuildAsync();

    RebuildStatus rebuildStatus();

    long countEmbeddedQuestions();

    boolean isEnabled();

    record SemanticSearchHit(Long questionId, double score) {
    }

    record RebuildStatus(
            boolean enabled,
            boolean running,
            int total,
            int processed,
            int successCount,
            int failCount,
            String message
    ) {
    }
}
