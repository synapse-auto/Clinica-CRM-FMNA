package com.synapse.clinicafemina.dto.darwin;

/**
 * Par id/nome usado em vários endpoints Darwin (procedimentos, convênios, grades).
 */
public record DarwinNamedRef(
        String id,
        String name
) {}
