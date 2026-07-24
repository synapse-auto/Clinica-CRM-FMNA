package com.synapse.clinicafemina.dto.darwin;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Corpo de resposta de GET /api/patients/find/cpf (coleção Postman Darwin v1.0.9).
 */
public record DarwinPatientRecordDTO(
        String cpf,
        String name,
        String rg,
        String cns,
        String job,
        String email,
        String gender,
        String skinColor,
        String socialName,
        String motherName,
        String fatherName,
        String naturalness,
        OffsetDateTime birthDate,
        String phoneNumber,
        List<ExtraPhone> extraPhones,
        DarwinAddress address
) {
    public record ExtraPhone(String phone) {}
}
