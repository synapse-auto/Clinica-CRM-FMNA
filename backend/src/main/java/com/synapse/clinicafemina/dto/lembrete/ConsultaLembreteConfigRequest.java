package com.synapse.clinicafemina.dto.lembrete;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

public record ConsultaLembreteConfigRequest(
        @NotBlank @Size(max = 120) String nome,
        String descricao,
        Boolean ativo,
        @NotBlank @Size(max = 40) String canal,
        @NotNull @PositiveOrZero Integer antecedenciaQuantidade,
        @NotBlank @Size(max = 20) String antecedenciaUnidade,
        LocalTime horarioEnvio,
        Boolean permiteConfirmacao,
        Boolean permiteCancelamento,
        Boolean permiteReagendamento,
        String mensagemTemplate,
        String configJson
) {
}
