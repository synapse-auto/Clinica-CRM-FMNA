package com.synapse.clinicafemina.dto.followup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record FollowUpTemporaryRequest(
        Long followUpConfigId,
        @NotBlank @Size(max = 160) String titulo,
        String descricao,
        @Size(max = 60) String origem,
        @Size(max = 40) String status,
        @NotNull OffsetDateTime scheduledAt,
        String payload
) {
}
