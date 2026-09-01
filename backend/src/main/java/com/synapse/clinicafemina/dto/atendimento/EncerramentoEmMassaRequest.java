package com.synapse.clinicafemina.dto.atendimento;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record EncerramentoEmMassaRequest(
        @NotNull @AssertTrue Boolean confirmado,
        @NotBlank @Pattern(regexp = "ENCERRAR TODOS") String confirmacao,
        String motivo
) {
}
