package com.synapse.clinicafemina.dto.equipe;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

public record EquipeUsuarioCreateRequest(
        @NotBlank(message = "Nome é obrigatório.")
        String nome,

        @NotBlank(message = "Email é obrigatório.")
        @Email(message = "Email inválido.")
        String email,

        @NotBlank(message = "Perfil é obrigatório.")
        String perfil,

        String telefone,

        @NotBlank(message = "Senha temporária é obrigatória.")
        @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, format = "password") String senhaTemporaria
) {
}
