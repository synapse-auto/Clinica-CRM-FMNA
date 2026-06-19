package com.synapse.clinicafemina.dto.agendamento;

import java.time.OffsetDateTime;

public record AgendamentoResponse(
        Long id,
        Long pacienteId,
        String pacienteNome,
        Long medicoId,
        String medicoNome,
        OffsetDateTime dataHoraInicio,
        OffsetDateTime dataHoraFim,
        String tipo,
        String servicoNome,
        String status,
        String origem,
        Integer confirmacaoEnviada,
        OffsetDateTime canceladoEm,
        String motivoCancelamento
) {
}
