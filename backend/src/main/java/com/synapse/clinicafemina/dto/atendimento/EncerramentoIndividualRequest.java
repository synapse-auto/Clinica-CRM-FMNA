package com.synapse.clinicafemina.dto.atendimento;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record EncerramentoIndividualRequest(
        @NotNull @AssertTrue Boolean confirmado,
        @NotBlank @Pattern(regexp = "DIALOG_ATENDIMENTO") String origem,
        @NotBlank @Pattern(regexp = "ENCERRAR") String confirmacao,
        String motivo
) {
}
