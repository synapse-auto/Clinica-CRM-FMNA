package com.synapse.clinicafemina.integration.external;

import java.time.OffsetDateTime;
import java.util.Map;

public record ExternalPatientDTO(
        String externalId,
        String fullName,
        String documentNumber,
        String email,
        String phone,
        String birthDate,
        OffsetDateTime updatedAt,
        Map<String, Object> payload
) {
    public ExternalPatientDTO {
        payload = ExternalPayloads.immutableNullSafeCopy(payload);
    }
}
