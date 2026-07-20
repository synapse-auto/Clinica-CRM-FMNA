package com.synapse.clinicafemina.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ExternalSyncLockServiceTest {

    @Test
    void should_keep_tests_on_non_postgres_without_attempting_postgres_advisory_function() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:external-sync-lock;DB_CLOSE_DELAY=-1", "sa", "");

        assertTrue(new ExternalSyncLockService(dataSource)
                .tryAcquire(1L, ExternalProviderType.MEDWARE)
                .isPresent());
    }
}
