package com.synapse.clinicafemina.dto.configuracao;

import com.synapse.clinicafemina.domain.TipoClinica;

public record ClinicaAtualResponse(
        String nome,
        String slug,
        TipoClinica tipoClinica,
        String corPrimaria,
        String logoUrl,
        boolean usaCirurgiasNaAgenda,
        boolean followUpAutomatico
) {
}
