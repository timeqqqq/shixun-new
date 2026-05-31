package com.campus.qa.service;

public interface QueryLogAsyncService {
    void saveLogAsync(String keyword, Long matchedQuestionId, String userIp);
}
