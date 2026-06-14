package com.campus.qa.service;

import com.campus.qa.vo.SearchAiAnswerResponse;

public interface AiAnswerService {
    SearchAiAnswerResponse answer(String question);
}
