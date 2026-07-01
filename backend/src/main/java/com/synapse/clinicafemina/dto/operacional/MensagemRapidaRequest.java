package com.synapse.clinicafemina.dto.operacional;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MensagemRapidaRequest(
        Short categoriaId,
        @NotBlank @Size(max = 120) String titulo,
        @NotBlank @Size(max = 40) String atalho,
        @NotBlank String conteudo,
        Boolean ativo
) {
}
