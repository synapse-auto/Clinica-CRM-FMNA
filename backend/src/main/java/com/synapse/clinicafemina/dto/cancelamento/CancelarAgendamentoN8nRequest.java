package com.synapse.clinicafemina.dto.cancelamento;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CancelarAgendamentoN8nRequest(
        @NotNull Long agendamentoId,
        Long atendimentoId,
        @NotBlank @Size(max = 2000) String motivo,
        @NotBlank String origem
) { }
