package com.synapse.clinicafemina.dto.operacional;

import com.synapse.clinicafemina.domain.UsoMensagemRapida;
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
        UsoMensagemRapida uso,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm
) {
}
