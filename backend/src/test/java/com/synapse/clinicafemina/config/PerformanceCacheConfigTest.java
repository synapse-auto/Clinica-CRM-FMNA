package com.synapse.clinicafemina.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PerformanceCacheConfigTest {

    @Test
    void should_create_named_bounded_caches_with_expected_ttl_and_stats() {
        CacheManager manager = new PerformanceCacheConfig().performanceCacheManager();

        assertCache(manager, PerformanceCacheConfig.DASHBOARD_SUMMARY, 500, Duration.ofSeconds(60));
        assertCache(manager, PerformanceCacheConfig.DASHBOARD_TIMESERIES, 500, Duration.ofSeconds(60));
        assertCache(manager, PerformanceCacheConfig.DASHBOARD_SERVICES, 500, Duration.ofSeconds(60));
        assertCache(manager, PerformanceCacheConfig.AGENDA_DOCTOR_DISTRIBUTION, 500, Duration.ofSeconds(30));
    }

    @Test
    void should_use_no_op_cache_when_disabled() {
        CacheManager manager = new PerformanceCacheConfig().noOpPerformanceCacheManager();
        manager.getCache(PerformanceCacheConfig.DASHBOARD_SUMMARY).put("key", "value");
        assertNull(manager.getCache(PerformanceCacheConfig.DASHBOARD_SUMMARY).get("key"));
    }

    @Test
    void should_expire_and_bound_entries_without_real_sleep() {
        AtomicLong nanos = new AtomicLong();
        com.github.benmanes.caffeine.cache.Cache<String, String> cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(60))
                .maximumSize(2)
                .ticker(nanos::get)
                .build();
        cache.put("first", "value");
        nanos.addAndGet(Duration.ofSeconds(61).toNanos());
        assertNull(cache.getIfPresent("first"));

        cache.put("one", "value");
        cache.put("two", "value");
        cache.put("three", "value");
        cache.cleanUp();
        assertEquals(2, cache.estimatedSize());
    }

    private void assertCache(CacheManager manager, String name, long maximum, Duration ttl) {
        CaffeineCache cache = (CaffeineCache) manager.getCache(name);
        assertNotNull(cache);
        assertEquals(maximum, cache.getNativeCache().policy().eviction().orElseThrow().getMaximum());
        assertEquals(ttl, cache.getNativeCache().policy().expireAfterWrite().orElseThrow().getExpiresAfter());
        cache.put("key", "value");
        cache.get("key");
        assertEquals(1, cache.getNativeCache().stats().hitCount());
    }
}
