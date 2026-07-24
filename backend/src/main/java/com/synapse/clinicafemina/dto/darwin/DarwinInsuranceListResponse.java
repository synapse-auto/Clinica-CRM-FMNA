package com.synapse.clinicafemina.dto.darwin;

import java.util.List;

/**
 * Corpo de resposta de GET /api/insurances/list/location (coleção Postman Darwin v1.0.9).
 */
public record DarwinInsuranceListResponse(
        List<DarwinNamedRef> insurances,
        DarwinPagination pagination
) {}
