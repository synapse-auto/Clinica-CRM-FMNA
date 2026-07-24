package com.synapse.clinicafemina.dto.darwin;

import java.util.List;

/**
 * Corpo de resposta de GET /api/procedures/list/location (coleção Postman Darwin v1.0.9).
 */
public record DarwinProcedureListResponse(
        List<DarwinNamedRef> procedures,
        DarwinPagination pagination
) {}
