package com.synapse.clinicafemina.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class PerformanceCacheConfig {

    public static final String DASHBOARD_SUMMARY = "dashboard-summary";
    public static final String DASHBOARD_TIMESERIES = "dashboard-timeseries";
    public static final String DASHBOARD_SERVICES = "dashboard-services";
    public static final String AGENDA_DOCTOR_DISTRIBUTION = "agenda-doctor-distribution";

    @Bean
    @ConditionalOnProperty(
            prefix = "app.performance.cache",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public CacheManager performanceCacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                cache(DASHBOARD_SUMMARY, Duration.ofSeconds(60), 500),
                cache(DASHBOARD_TIMESERIES, Duration.ofSeconds(60), 500),
                cache(DASHBOARD_SERVICES, Duration.ofSeconds(60), 500),
                cache(AGENDA_DOCTOR_DISTRIBUTION, Duration.ofSeconds(30), 500)
        ));
        manager.initializeCaches();
        return manager;
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "app.performance.cache",
            name = "enabled",
            havingValue = "false"
    )
    public CacheManager noOpPerformanceCacheManager() {
        return new NoOpCacheManager();
    }

    private CaffeineCache cache(String name, Duration ttl, long maximumSize) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(maximumSize)
                .recordStats()
                .build());
    }
}
