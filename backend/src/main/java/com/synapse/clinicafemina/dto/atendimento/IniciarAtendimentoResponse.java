package com.synapse.clinicafemina.dto.atendimento;

import com.synapse.clinicafemina.dto.AtendimentoDetalheDTO;

public record IniciarAtendimentoResponse(
        Long atendimentoId,
        Long pacienteId,
        String modo,
        boolean pacienteCriado,
        boolean atendimentoCriado,
        boolean atendimentoReutilizado,
        boolean destinatarioAlterado,
        AtendimentoDetalheDTO atendimento
) {
}
