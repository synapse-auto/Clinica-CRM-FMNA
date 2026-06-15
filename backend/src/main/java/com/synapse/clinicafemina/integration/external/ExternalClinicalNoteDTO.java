package com.synapse.clinicafemina.integration.external;

import java.time.OffsetDateTime;
import java.util.Map;

public record ExternalClinicalNoteDTO(
        String externalId,
        String externalPatientId,
        String content,
        OffsetDateTime createdAt,
        Map<String, Object> payload
) {
    public ExternalClinicalNoteDTO {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
