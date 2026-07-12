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
        String motivoCancelamento,
        String medicoExternalId,
        String medicoOrigem
) {
    public AgendamentoResponse(
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
        this(id, pacienteId, pacienteNome, medicoId, medicoNome, dataHoraInicio, dataHoraFim,
                tipo, servicoNome, status, origem, confirmacaoEnviada, canceladoEm,
                motivoCancelamento, null, null);
    }
}
