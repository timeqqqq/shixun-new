package com.campus.qa.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.campus.qa.dto.CrawlStartRequest;
import com.campus.qa.dto.CrawlStartResponse;
import com.campus.qa.entity.CrawlTask;
import com.campus.qa.entity.Question;
import com.campus.qa.mapper.CrawlTaskMapper;
import com.campus.qa.mapper.QuestionMapper;
import com.campus.qa.service.CrawlService;
import com.campus.qa.service.QuestionEmbeddingService;
import java.time.LocalDateTime;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CrawlServiceImpl implements CrawlService {

    private final CrawlTaskMapper crawlTaskMapper;
    private final QuestionMapper questionMapper;
    private final QuestionEmbeddingService questionEmbeddingService;

    public CrawlServiceImpl(CrawlTaskMapper crawlTaskMapper,
                            QuestionMapper questionMapper,
                            QuestionEmbeddingService questionEmbeddingService) {
        this.crawlTaskMapper = crawlTaskMapper;
        this.questionMapper = questionMapper;
        this.questionEmbeddingService = questionEmbeddingService;
    }

    @Override
    public CrawlStartResponse startCrawl(CrawlStartRequest request) {
        CrawlTask task = new CrawlTask();
        task.setTaskName(request.getTaskName().trim());
        task.setTargetUrl(request.getTargetUrl().trim());
        task.setStatus("running");
        task.setTotalFound(0);
        task.setTotalInserted(0);
        task.setStartTime(LocalDateTime.now());
        crawlTaskMapper.insert(task);

        try {
            Document doc = Jsoup.connect(task.getTargetUrl())
                    .userAgent("CampusQA-Bot/1.0")
                    .timeout(10000)
                    .get();

            String title = safeTrim(doc.title());
            String content = extractMainText(doc);

            int totalFound = 0;
            int totalInserted = 0;
            if (StringUtils.hasText(title) && StringUtils.hasText(content)) {
                totalFound = 1;

                QueryWrapper<Question> wrapper = new QueryWrapper<>();
                wrapper.eq("question", title);
                Question exist = questionMapper.selectOne(wrapper);
                if (exist == null) {
                    Question question = new Question();
                    question.setQuestion(title);
                    question.setAnswer(content);
                    question.setCategory("\u7f51\u7ad9\u4fe1\u606f");
                    question.setSource("crawl");
                    question.setHitCount(0L);
                    question.setCreateTime(LocalDateTime.now());
                    questionMapper.insert(question);
                    questionEmbeddingService.upsertEmbedding(question);
                    totalInserted = 1;
                }
            }

            task.setStatus("success");
            task.setTotalFound(totalFound);
            task.setTotalInserted(totalInserted);
            task.setEndTime(LocalDateTime.now());
            crawlTaskMapper.updateById(task);

            return CrawlStartResponse.builder()
                    .taskId(task.getId())
                    .status(task.getStatus())
                    .totalFound(totalFound)
                    .totalInserted(totalInserted)
                    .message("\u722c\u53d6\u5b8c\u6210")
                    .build();
        } catch (Exception ex) {
            task.setStatus("failed");
            task.setEndTime(LocalDateTime.now());
            task.setErrorMessage(shortMsg(ex.getMessage()));
            crawlTaskMapper.updateById(task);

            return CrawlStartResponse.builder()
                    .taskId(task.getId())
                    .status(task.getStatus())
                    .totalFound(0)
                    .totalInserted(0)
                    .message("\u722c\u53d6\u5931\u8d25: " + shortMsg(ex.getMessage()))
                    .build();
        }
    }

    @Override
    public List<CrawlTask> listTasks() {
        QueryWrapper<CrawlTask> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("id");
        wrapper.last("LIMIT 50");
        return crawlTaskMapper.selectList(wrapper);
    }

    private static String extractMainText(Document doc) {
        Element body = doc.body();
        if (body == null) {
            return "";
        }
        body.select("script,style,nav,header,footer,noscript").remove();
        String text = safeTrim(body.text());
        if (text.length() > 2000) {
            return text.substring(0, 2000);
        }
        return text;
    }

    private static String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }

    private static String shortMsg(String msg) {
        String m = msg == null ? "unknown" : msg;
        return m.length() > 200 ? m.substring(0, 200) : m;
    }
}
