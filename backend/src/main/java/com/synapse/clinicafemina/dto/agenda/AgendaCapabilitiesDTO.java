package com.synapse.clinicafemina.dto.agenda;

/**
 * Capacidades reais do provider da Agenda para a clínica autenticada. O frontend deve
 * usar exclusivamente este contrato para decidir o que exibir/habilitar — nunca inferir
 * capacidade por sucesso ou falha de uma chamada de catálogo.
 */
public record AgendaCapabilitiesDTO(
        String provider,
        boolean supportsCatalog,
        boolean supportsWriteOperations,
        boolean supportsFitIn,
        boolean supportsClinicWideListing,
        boolean supportsPatientLookup,
        boolean supportsBackfill,
        String coverage
) {}
