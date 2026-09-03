package com.synapse.clinicafemina.dto.atendimento;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record EncerramentoIndividualRequest(
        @NotNull @AssertTrue Boolean confirmado,
        String motivo
) {
}
