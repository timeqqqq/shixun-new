package com.campus.qa.service.stats;

import java.util.Map;

public interface CacheStatsService {
    void incTotal();

    void incHit();

    void reset();

    void clearSearchCache();

    Map<String, Object> snapshot();
}
