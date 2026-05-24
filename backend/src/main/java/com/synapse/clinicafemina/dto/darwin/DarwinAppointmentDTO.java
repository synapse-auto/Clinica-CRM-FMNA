package com.synapse.clinicafemina.dto.darwin;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public record DarwinAppointmentDTO(
        String id,
        @JsonProperty("patient_id") String patientId,
        @JsonProperty("scheduled_time") OffsetDateTime scheduledTime,
        String status,
        @JsonProperty("updated_at") OffsetDateTime updatedAt
) {}
