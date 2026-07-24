package com.synapse.clinicafemina.dto.darwin;

/**
 * Item de resposta de GET /api/locations/list/professional (coleção Postman Darwin v1.0.9).
 */
public record DarwinLocationRef(
        String locationId,
        String locationName
) {}
