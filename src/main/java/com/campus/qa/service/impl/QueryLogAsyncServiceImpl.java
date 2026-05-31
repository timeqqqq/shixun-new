package com.campus.qa.service.impl;

import com.campus.qa.entity.QueryLog;
import com.campus.qa.mapper.QueryLogMapper;
import com.campus.qa.service.QueryLogAsyncService;
import java.time.LocalDateTime;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class QueryLogAsyncServiceImpl implements QueryLogAsyncService {

    private final QueryLogMapper queryLogMapper;

    public QueryLogAsyncServiceImpl(QueryLogMapper queryLogMapper) {
        this.queryLogMapper = queryLogMapper;
    }

    @Override
    @Async
    public void saveLogAsync(String keyword, Long matchedQuestionId, String userIp) {
        QueryLog log = new QueryLog();
        log.setKeyword(keyword);
        log.setMatchedQuestionId(matchedQuestionId);
        log.setUserIp(userIp == null ? "unknown" : userIp);
        log.setQueryTime(LocalDateTime.now());
        queryLogMapper.insert(log);
    }
}
