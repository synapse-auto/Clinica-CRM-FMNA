package com.synapse.clinicafemina.dto.agendamento;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgendamentoCancelRequest(
        @NotBlank @Size(max = 255) String motivo
) {
}
