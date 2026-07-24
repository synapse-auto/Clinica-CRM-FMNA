package com.synapse.clinicafemina.dto.darwin;

/**
 * Corpo de GET /api/integracoes/darwin/status. Nunca inclui token, base URL,
 * CPF ou dados de paciente — apenas metadados de capacidade da integração.
 */
public record DarwinStatusResponse(
        boolean enabled,
        String provider,
        boolean configured,
        boolean bulkSyncSupported,
        boolean onDemandQueriesSupported
) {}
