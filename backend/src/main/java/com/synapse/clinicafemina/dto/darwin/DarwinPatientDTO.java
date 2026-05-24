package com.synapse.clinicafemina.dto.darwin;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public record DarwinPatientDTO(
        String id,
        @JsonProperty("full_name") String fullName,
        @JsonProperty("document_number") String documentNumber,
        String email,
        String phone,
        @JsonProperty("birth_date") String birthDate,
        @JsonProperty("updated_at") OffsetDateTime updatedAt
) {}
