package com.synapse.clinicafemina.integration.external;

import java.time.OffsetDateTime;
import java.util.Map;

public record ExternalAppointmentDTO(
        String externalId,
        String externalPatientId,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        String type,
        String serviceName,
        String status,
        OffsetDateTime confirmedAt,
        OffsetDateTime canceledAt,
        String cancellationReason,
        Map<String, Object> payload
) {
    public ExternalAppointmentDTO {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
