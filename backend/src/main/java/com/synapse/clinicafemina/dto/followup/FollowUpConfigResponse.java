package com.synapse.clinicafemina.dto.followup;

import java.time.LocalTime;
import java.time.OffsetDateTime;

public record FollowUpConfigResponse(
        Long id,
        Long clinicaId,
        String nome,
        String descricao,
        Boolean ativo,
        String gatilho,
        String canal,
        Integer delayQuantidade,
        String delayUnidade,
        LocalTime horarioEnvio,
        String mensagemTemplate,
        String configJson,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
