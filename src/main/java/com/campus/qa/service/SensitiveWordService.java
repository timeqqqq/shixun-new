package com.campus.qa.service;

import com.campus.qa.entity.SensitiveWord;
import java.util.List;

public interface SensitiveWordService {

    record ModerationHit(boolean hit, String word, String level) {}

    List<SensitiveWord> listAll();

    SensitiveWord add(String word, String level, Integer enabled, String source);

    void deleteById(Long id);

    void setEnabled(Long id, Integer enabled);

    int refresh();

    ModerationHit match(String text);
}
