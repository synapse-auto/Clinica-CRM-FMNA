package com.synapse.clinicafemina.dto.darwin;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Corpo de POST /api/patients/create (coleção Postman Darwin v1.0.9).
 * Também reaproveitado como objeto "patient" aninhado em POST /schedules/create e
 * POST /schedules/create/fitin, que documentam o mesmo formato.
 * Campos ausentes (null) não são enviados — conforme documentação, são opcionais.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DarwinCreatePatientRequest(
        String name,
        String cpf,
        String rg,
        String cns,
        String job,
        String email,
        String socialName,
        String motherName,
        String fatherName,
        String naturalness,
        String gender,
        String phoneNumber,
        List<DarwinExtraPhone> extraPhones,
        String skinColor,
        String birthDate,
        DarwinAddress address
) {}
