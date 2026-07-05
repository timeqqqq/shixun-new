package com.campus.qa.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.qa.dto.admin.EditApproveRequest;
import com.campus.qa.entity.Contribution;
import com.campus.qa.entity.Question;
import com.campus.qa.mapper.ContributionMapper;
import com.campus.qa.mapper.QuestionMapper;
import com.campus.qa.service.AdminContributionAuditService;
import com.campus.qa.service.QuestionEmbeddingService;
import com.campus.qa.service.stats.CacheStatsService;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AdminContributionAuditServiceImpl implements AdminContributionAuditService {

    private final ContributionMapper contributionMapper;
    private final QuestionMapper questionMapper;
    private final QuestionEmbeddingService questionEmbeddingService;
    private final CacheStatsService cacheStatsService;

    public AdminContributionAuditServiceImpl(ContributionMapper contributionMapper,
                                            QuestionMapper questionMapper,
                                            QuestionEmbeddingService questionEmbeddingService,
                                            CacheStatsService cacheStatsService) {
        this.contributionMapper = contributionMapper;
        this.questionMapper = questionMapper;
        this.questionEmbeddingService = questionEmbeddingService;
        this.cacheStatsService = cacheStatsService;
    }

    @Override
    public IPage<Map<String, Object>> list(int page, int size, String status) {
        QueryWrapper<Contribution> wrapper = new QueryWrapper<>();
        if (StringUtils.hasText(status)) {
            wrapper.eq("status", status);
        }
        wrapper.orderByDesc("id");
        IPage<Contribution> raw = contributionMapper.selectPage(new Page<>(page, size), wrapper);

        IPage<Map<String, Object>> out = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        List<Map<String, Object>> records = raw.getRecords().stream().map(c -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getId());
            m.put("question", c.getQuestion());
            m.put("answer", c.getAnswer());
            m.put("category", c.getCategory());
            m.put("status", c.getStatus());
            m.put("submitIp", c.getSubmitIp());
            m.put("submitTime", c.getSubmitTime());
            m.put("auditTime", c.getAuditTime());
            m.put("rejectReason", c.getRejectReason());
            return m;
        }).toList();
        out.setRecords(records);
        return out;
    }

    @Override
    public void approve(Long id) {
        Contribution c = mustGet(id);
        if (!"pending".equals(c.getStatus())) {
            throw new IllegalArgumentException("only pending contribution can be approved");
        }
        insertQuestionIfAbsent(c.getQuestion(), c.getAnswer(), c.getCategory(), "contribution");
        c.setStatus("approved");
        c.setAuditTime(LocalDateTime.now());
        c.setRejectReason(null);
        contributionMapper.updateById(c);
    }

    @Override
    public void reject(Long id, String reason) {
        Contribution c = mustGet(id);
        if (!"pending".equals(c.getStatus())) {
            throw new IllegalArgumentException("only pending contribution can be rejected");
        }
        c.setStatus("rejected");
        c.setAuditTime(LocalDateTime.now());
        c.setRejectReason(StringUtils.hasText(reason) ? reason.trim() : "rejected by admin");
        contributionMapper.updateById(c);
    }

    @Override
    public void editAndApprove(Long id, EditApproveRequest request) {
        Contribution c = mustGet(id);
        if (!"pending".equals(c.getStatus())) {
            throw new IllegalArgumentException("only pending contribution can be edited and approved");
        }
        String q = request.getQuestion().trim();
        String a = request.getAnswer().trim();
        String cat = request.getCategory() == null ? "" : request.getCategory().trim();

        c.setQuestion(q);
        c.setAnswer(a);
        c.setCategory(cat);
        c.setStatus("approved");
        c.setAuditTime(LocalDateTime.now());
        c.setRejectReason(null);
        contributionMapper.updateById(c);

        insertQuestionIfAbsent(q, a, cat, "contribution");
    }

    @Override
    public void batchReject(List<Long> ids, String reason) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("ids must not be empty");
        }
        String r = StringUtils.hasText(reason) ? reason.trim() : "batch rejected by admin";
        for (Long id : ids) {
            Contribution c = contributionMapper.selectById(id);
            if (c == null || !"pending".equals(c.getStatus())) {
                continue;
            }
            c.setStatus("rejected");
            c.setAuditTime(LocalDateTime.now());
            c.setRejectReason(r);
            contributionMapper.updateById(c);
        }
    }

    private Contribution mustGet(Long id) {
        Contribution c = contributionMapper.selectById(id);
        if (c == null) {
            throw new IllegalArgumentException("contribution not found: " + id);
        }
        return c;
    }

    private void insertQuestionIfAbsent(String question, String answer, String category, String source) {
        QueryWrapper<Question> q = new QueryWrapper<>();
        q.eq("question", question);
        if (questionMapper.selectCount(q) > 0) {
            return;
        }
        Question item = new Question();
        item.setQuestion(question);
        item.setAnswer(answer);
        item.setCategory(category);
        item.setSource(source);
        item.setHitCount(0L);
        item.setCreateTime(LocalDateTime.now());
        questionMapper.insert(item);
        questionEmbeddingService.upsertEmbedding(item);
        cacheStatsService.clearSearchCache();
    }
}
