package com.synapse.clinicafemina.dto.lembrete;

import java.time.OffsetDateTime;

public record MensagemFestivaConfigResponse(
        Long id,
        Long clinicaId,
        String chave,
        String nome,
        String mesDia,
        Boolean ativo,
        String canal,
        String mensagemTemplate,
        String configJson,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
