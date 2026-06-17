package com.synapse.clinicafemina.dto.lembrete;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MensagemFestivaConfigRequest(
        @NotBlank @Size(max = 80) String chave,
        @NotBlank @Size(max = 120) String nome,
        @NotBlank @Pattern(regexp = "^(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])$") String mesDia,
        Boolean ativo,
        @NotBlank @Size(max = 40) String canal,
        @NotBlank String mensagemTemplate,
        String configJson
) {
}
