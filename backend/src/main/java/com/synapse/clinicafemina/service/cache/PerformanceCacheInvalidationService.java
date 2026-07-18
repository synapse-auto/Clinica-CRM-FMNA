package com.synapse.clinicafemina.service.cache;

import com.synapse.clinicafemina.config.PerformanceCacheConfig;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@RequiredArgsConstructor
public class PerformanceCacheInvalidationService {

    private static final List<String> CLINIC_SCOPED_CACHES = List.of(
            PerformanceCacheConfig.DASHBOARD_SUMMARY,
            PerformanceCacheConfig.DASHBOARD_TIMESERIES,
            PerformanceCacheConfig.DASHBOARD_SERVICES,
            PerformanceCacheConfig.AGENDA_DOCTOR_DISTRIBUTION
    );

    private final CacheManager cacheManager;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void invalidateAfterCommit(ClinicDataChangedEvent event) {
        evictClinic(event.clinicId());
    }

    public void evictClinic(Long clinicId) {
        if (clinicId == null) {
            return;
        }
        CLINIC_SCOPED_CACHES.forEach(cacheName -> evictClinic(cacheManager.getCache(cacheName), clinicId));
    }

    private void evictClinic(Cache cache, Long clinicId) {
        if (!(cache instanceof CaffeineCache caffeineCache)) {
            return;
        }
        caffeineCache.getNativeCache().asMap().keySet().removeIf(key ->
                key instanceof ClinicScopedCacheKey scoped
                        && Objects.equals(scoped.clinicId(), clinicId));
    }
}
