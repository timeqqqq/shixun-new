package com.campus.qa.service;

import com.campus.qa.vo.HotQuestionItem;
import java.util.List;

public interface HotService {
    List<HotQuestionItem> listHot(String period);

    void pinQuestion(Long questionId);

    void refreshAllCache();
}
