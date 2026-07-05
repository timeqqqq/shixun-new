package com.campus.qa.service.impl.stats;

import com.campus.qa.service.stats.CacheStatsService;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class CacheStatsServiceImpl implements CacheStatsService {

    private static final String KEY_TOTAL = "cache:stats:search:total";
    private static final String KEY_HIT = "cache:stats:search:hit";
    private static final String SEARCH_PATTERN = "search:*";

    private final RedisTemplate<Object, Object> redisTemplate;

    public CacheStatsServiceImpl(RedisTemplate<Object, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void incTotal() {
        try {
            redisTemplate.opsForValue().increment(KEY_TOTAL);
        } catch (Exception ignore) {
            // Ignore Redis failures to avoid affecting search flow.
        }
    }

    @Override
    public void incHit() {
        try {
            redisTemplate.opsForValue().increment(KEY_HIT);
        } catch (Exception ignore) {
            // Ignore Redis failures to avoid affecting search flow.
        }
    }

    @Override
    public void reset() {
        try {
            redisTemplate.delete(KEY_TOTAL);
            redisTemplate.delete(KEY_HIT);
        } catch (Exception ignore) {
            // Ignore Redis failures.
        }
    }

    @Override
    public Map<String, Object> snapshot() {
        long total = getLong(KEY_TOTAL);
        long hit = getLong(KEY_HIT);
        double rate = total == 0 ? 0.0 : (hit * 100.0 / total);
        long searchCacheKeyCount = countSearchCacheKeys();
        return Map.of(
                "totalRequests", total,
                "cacheHits", hit,
                "hitRate", Math.round(rate * 100.0) / 100.0,
                "searchCacheKeyCount", searchCacheKeyCount
        );
    }

    private long getLong(String key) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value instanceof Number number) {
                return number.longValue();
            }
            if (value instanceof String text) {
                return Long.parseLong(text);
            }
        } catch (Exception ignore) {
            // Ignore Redis failures.
        }
        return 0L;
    }

    private long countSearchCacheKeys() {
        try {
            Set<Object> keys = redisTemplate.keys(SEARCH_PATTERN);
            return keys == null ? 0L : keys.size();
        } catch (Exception ignore) {
            return 0L;
        }
    }
}
