package com.synapse.clinicafemina.dto.lembrete;

import java.time.LocalTime;
import java.time.OffsetDateTime;

public record ConsultaLembreteConfigResponse(
        Long id,
        Long clinicaId,
        String nome,
        String descricao,
        Boolean ativo,
        String canal,
        Integer antecedenciaQuantidade,
        String antecedenciaUnidade,
        LocalTime horarioEnvio,
        Boolean permiteConfirmacao,
        Boolean permiteCancelamento,
        Boolean permiteReagendamento,
        String mensagemTemplate,
        String configJson,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
