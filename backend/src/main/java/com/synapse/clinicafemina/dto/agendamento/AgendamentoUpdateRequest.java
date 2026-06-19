package com.synapse.clinicafemina.dto.agendamento;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record AgendamentoUpdateRequest(
        @NotNull Long pacienteId,
        Long medicoId,
        @NotNull OffsetDateTime dataHoraInicio,
        OffsetDateTime dataHoraFim,
        @NotBlank @Size(max = 40) String tipo,
        @NotBlank @Size(max = 120) String servicoNome
) {
}
