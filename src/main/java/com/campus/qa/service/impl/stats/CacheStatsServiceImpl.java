package com.campus.qa.service.impl.stats;

import com.campus.qa.service.stats.CacheStatsService;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class CacheStatsServiceImpl implements CacheStatsService {

    private final AtomicLong total = new AtomicLong(0);
    private final AtomicLong hit = new AtomicLong(0);

    @Override
    public void incTotal() {
        total.incrementAndGet();
    }

    @Override
    public void incHit() {
        hit.incrementAndGet();
    }

    @Override
    public void reset() {
        total.set(0);
        hit.set(0);
    }

    @Override
    public Map<String, Object> snapshot() {
        long t = total.get();
        long h = hit.get();
        double rate = t == 0 ? 0.0 : (h * 100.0 / t);
        return Map.of(
                "totalRequests", t,
                "cacheHits", h,
                "hitRate", Math.round(rate * 100.0) / 100.0
        );
    }
}
