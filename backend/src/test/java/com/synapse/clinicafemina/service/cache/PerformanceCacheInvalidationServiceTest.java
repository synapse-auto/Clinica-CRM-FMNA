package com.synapse.clinicafemina.service.cache;

import com.synapse.clinicafemina.config.PerformanceCacheConfig;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PerformanceCacheInvalidationServiceTest {

    @Test
    void should_evict_only_entries_from_changed_clinic() {
        CacheManager manager = new PerformanceCacheConfig().performanceCacheManager();
        Cache cache = manager.getCache(PerformanceCacheConfig.DASHBOARD_SUMMARY);
        DashboardCacheKey clinicOne = key(1L);
        DashboardCacheKey clinicTwo = key(2L);
        cache.put(clinicOne, "one");
        cache.put(clinicTwo, "two");

        new PerformanceCacheInvalidationService(manager).evictClinic(1L);

        assertNull(cache.get(clinicOne));
        assertNotNull(cache.get(clinicTwo));
    }

    private DashboardCacheKey key(Long clinicId) {
        return new DashboardCacheKey(
                clinicId,
                OffsetDateTime.parse("2026-07-01T00:00:00-03:00"),
                OffsetDateTime.parse("2026-08-01T00:00:00-03:00"),
                "America/Sao_Paulo",
                true
        );
    }
}
