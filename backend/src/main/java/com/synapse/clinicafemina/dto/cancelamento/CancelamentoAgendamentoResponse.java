package com.synapse.clinicafemina.dto.cancelamento;

import java.time.OffsetDateTime;

public record CancelamentoAgendamentoResponse(
        Long id, Long pacienteId, String pacienteNome, String telefoneMascarado, Long agendamentoId,
        OffsetDateTime dataHoraAgendamento, String profissional, String servico, String motivo, String origem,
        String statusCancelamento, String statusSincronizacao, OffsetDateTime coletadoEm
) { }
