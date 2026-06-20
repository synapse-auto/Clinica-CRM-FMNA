package com.synapse.clinicafemina.dto;

import java.time.OffsetDateTime;

public record NotificacaoAtendimentoDTO(
        Long id,
        Long atendimentoId,
        String tipo,
        String descricao,
        boolean lida,
        OffsetDateTime criadoEm
) {
}
