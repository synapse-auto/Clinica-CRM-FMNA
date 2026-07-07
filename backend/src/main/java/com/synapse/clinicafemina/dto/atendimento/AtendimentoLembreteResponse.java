package com.synapse.clinicafemina.dto.atendimento;

import java.time.OffsetDateTime;

public record AtendimentoLembreteResponse(
        Long id,
        Long atendimentoId,
        String mensagem,
        OffsetDateTime lembrarEm,
        String status,
        Long criadoPorId,
        String criadoPorNome,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm
) {
}
