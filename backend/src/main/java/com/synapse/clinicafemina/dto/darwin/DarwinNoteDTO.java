package com.synapse.clinicafemina.dto.darwin;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public record DarwinNoteDTO(
        String id,
        @JsonProperty("patient_id") String patientId,
        String content,
        @JsonProperty("created_at") OffsetDateTime createdAt
) {}
