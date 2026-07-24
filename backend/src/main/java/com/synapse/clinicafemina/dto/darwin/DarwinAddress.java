package com.synapse.clinicafemina.dto.darwin;

public record DarwinAddress(
        String cep,
        String city,
        String state,
        Integer number,
        String address,
        String complement,
        String neighborhood
) {}
