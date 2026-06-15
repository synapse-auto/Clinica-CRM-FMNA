package com.synapse.clinicafemina.repository;

import com.synapse.clinicafemina.domain.IntegrationSyncLog;
import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IntegrationSyncLogRepository extends JpaRepository<IntegrationSyncLog, Long> {

    @Query("""
            SELECT l FROM IntegrationSyncLog l
            WHERE l.clinica.id = :clinicaId
              AND l.externalProvider = :provider
              AND l.status = 'SUCESSO'
            ORDER BY l.iniciadoEm DESC
            LIMIT 1
            """)
    Optional<IntegrationSyncLog> findUltimoSucesso(@Param("clinicaId") Long clinicaId,
                                                   @Param("provider") ExternalProviderType provider);
}
