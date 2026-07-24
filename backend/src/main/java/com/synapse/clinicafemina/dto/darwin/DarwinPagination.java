package com.synapse.clinicafemina.dto.darwin;

public record DarwinPagination(
        int page,
        int amount,
        int totalItems,
        int totalPages
) {}
