package com.synapse.clinicafemina.dto.operacional;

import java.time.OffsetDateTime;

public record TagResponse(
        Long id,
        String nome,
        String cor,
        String descricao,
        boolean ativo,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm
) {
}
