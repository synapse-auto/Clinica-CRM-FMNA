package com.synapse.clinicafemina.dto.followup;

import java.time.OffsetDateTime;

public record FollowUpTemporaryResponse(
        Long id,
        Long clinicaId,
        Long pacienteId,
        Long followUpConfigId,
        String titulo,
        String descricao,
        String origem,
        String status,
        OffsetDateTime scheduledAt,
        OffsetDateTime processedAt,
        OffsetDateTime canceledAt,
        String cancelReason,
        String payload,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
