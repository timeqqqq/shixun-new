package com.campus.qa.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.campus.qa.entity.SensitiveWord;
import com.campus.qa.mapper.SensitiveWordMapper;
import com.campus.qa.service.SensitiveWordService;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SensitiveWordServiceImpl implements SensitiveWordService {

    private final SensitiveWordMapper sensitiveWordMapper;

    private volatile TrieNode root = new TrieNode();

    public SensitiveWordServiceImpl(SensitiveWordMapper sensitiveWordMapper) {
        this.sensitiveWordMapper = sensitiveWordMapper;
    }

    @PostConstruct
    public void init() {
        refresh();
    }

    @Override
    public List<SensitiveWord> listAll() {
        QueryWrapper<SensitiveWord> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("id");
        return sensitiveWordMapper.selectList(wrapper);
    }

    @Override
    public SensitiveWord add(String word, String level, Integer enabled, String source) {
        String w = normalize(word);
        if (!StringUtils.hasText(w)) {
            throw new IllegalArgumentException("word must not be blank");
        }
        SensitiveWord item = new SensitiveWord();
        item.setWord(w);
        item.setLevel(normalizeLevel(level));
        item.setEnabled(enabled == null ? 1 : (enabled == 0 ? 0 : 1));
        item.setSource(StringUtils.hasText(source) ? source.trim() : "manual");
        sensitiveWordMapper.insert(item);
        refresh();
        return item;
    }

    @Override
    public void deleteById(Long id) {
        sensitiveWordMapper.deleteById(id);
        refresh();
    }

    @Override
    public void setEnabled(Long id, Integer enabled) {
        SensitiveWord item = sensitiveWordMapper.selectById(id);
        if (item == null) {
            throw new IllegalArgumentException("sensitive word not found: " + id);
        }
        item.setEnabled(enabled == null ? 1 : (enabled == 0 ? 0 : 1));
        sensitiveWordMapper.updateById(item);
        refresh();
    }

    @Override
    public int refresh() {
        QueryWrapper<SensitiveWord> wrapper = new QueryWrapper<>();
        wrapper.eq("enabled", 1);
        List<SensitiveWord> words = sensitiveWordMapper.selectList(wrapper);

        TrieNode newRoot = new TrieNode();
        int loaded = 0;
        for (SensitiveWord item : words) {
            String w = normalize(item.getWord());
            if (!StringUtils.hasText(w)) {
                continue;
            }
            insert(newRoot, w, normalizeLevel(item.getLevel()));
            loaded++;
        }
        this.root = newRoot;
        return loaded;
    }

    @Override
    public ModerationHit match(String text) {
        String input = normalize(text);
        if (!StringUtils.hasText(input)) {
            return new ModerationHit(false, null, null);
        }

        TrieNode currentRoot = this.root;
        int n = input.length();
        for (int i = 0; i < n; i++) {
            TrieNode node = currentRoot;
            for (int j = i; j < n; j++) {
                char c = input.charAt(j);
                node = node.children.get(c);
                if (node == null) {
                    break;
                }
                if (node.end) {
                    return new ModerationHit(true, input.substring(i, j + 1), node.level);
                }
            }
        }
        return new ModerationHit(false, null, null);
    }

    private static void insert(TrieNode root, String word, String level) {
        TrieNode node = root;
        for (char c : word.toCharArray()) {
            node = node.children.computeIfAbsent(c, k -> new TrieNode());
        }
        node.end = true;
        node.level = level;
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeLevel(String level) {
        if (!StringUtils.hasText(level)) {
            return "high";
        }
        String v = level.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "low", "medium", "high" -> v;
            default -> "high";
        };
    }

    private static class TrieNode {
        private final Map<Character, TrieNode> children = new HashMap<>();
        private boolean end;
        private String level;
    }
}
