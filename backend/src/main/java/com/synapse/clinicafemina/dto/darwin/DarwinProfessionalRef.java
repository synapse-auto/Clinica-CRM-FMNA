package com.synapse.clinicafemina.dto.darwin;

/**
 * Item de resposta de GET /api/professionals/list/locations (coleção Postman Darwin v1.0.9).
 */
public record DarwinProfessionalRef(
        String professionalId,
        String professionalName
) {}
