package com.synapse.clinicafemina.service.cache;

import com.synapse.clinicafemina.config.PerformanceCacheConfig;
import java.time.OffsetDateTime;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = PerformanceCacheTransactionListenerTest.TestConfig.class)
class PerformanceCacheTransactionListenerTest {

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private ClinicDataChangePublisher publisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Cache cache;
    private DashboardCacheKey key;

    @BeforeEach
    void setUp() {
        cache = cacheManager.getCache(PerformanceCacheConfig.DASHBOARD_SUMMARY);
        cache.clear();
        key = new DashboardCacheKey(
                1L,
                OffsetDateTime.parse("2026-07-01T00:00:00-03:00"),
                OffsetDateTime.parse("2026-08-01T00:00:00-03:00"),
                "America/Sao_Paulo",
                true
        );
    }

    @Test
    void should_invalidate_only_after_commit() {
        cache.put(key, "cached");

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            publisher.publish(1L);
            assertNotNull(cache.get(key));
        });

        assertNull(cache.get(key));
    }

    @Test
    void should_not_invalidate_when_transaction_rolls_back() {
        cache.put(key, "cached");

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            publisher.publish(1L);
            status.setRollbackOnly();
        });

        assertNotNull(cache.get(key));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        CacheManager cacheManager() {
            return new PerformanceCacheConfig().performanceCacheManager();
        }

        @Bean
        PerformanceCacheInvalidationService invalidationService(CacheManager cacheManager) {
            return new PerformanceCacheInvalidationService(cacheManager);
        }

        @Bean
        ClinicDataChangePublisher publisher(ApplicationEventPublisher applicationEventPublisher) {
            return new ClinicDataChangePublisher(applicationEventPublisher);
        }

        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:cache-events;DB_CLOSE_DELAY=-1", "sa", "");
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }
}
