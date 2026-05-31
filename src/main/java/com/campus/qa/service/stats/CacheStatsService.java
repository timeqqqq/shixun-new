package com.campus.qa.service.stats;

import java.util.Map;

public interface CacheStatsService {
    void incTotal();

    void incHit();

    Map<String, Object> snapshot();
}
