package com.synapse.clinicafemina.dto.operacional;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TagRequest(
        @NotBlank @Size(max = 80) String nome,
        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Cor deve estar no formato hexadecimal #RRGGBB.")
        String cor,
        String descricao,
        Boolean ativo
) {
}
