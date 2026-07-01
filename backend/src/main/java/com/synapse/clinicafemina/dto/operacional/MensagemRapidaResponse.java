package com.synapse.clinicafemina.dto.operacional;

import java.time.OffsetDateTime;

public record MensagemRapidaResponse(
        Long id,
        Short categoriaId,
        String categoriaRotulo,
        String categoriaCor,
        String titulo,
        String atalho,
        String conteudo,
        boolean ativo,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm
) {
}
