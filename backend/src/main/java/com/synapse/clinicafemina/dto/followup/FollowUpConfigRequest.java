package com.synapse.clinicafemina.dto.followup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

public record FollowUpConfigRequest(
        @NotBlank @Size(max = 120) String nome,
        String descricao,
        Boolean ativo,
        @NotBlank @Size(max = 80) String gatilho,
        @NotBlank @Size(max = 40) String canal,
        @PositiveOrZero Integer delayQuantidade,
        @Size(max = 20) String delayUnidade,
        LocalTime horarioEnvio,
        String mensagemTemplate,
        String configJson
) {
}
