package com.synapse.clinicafemina.dto.equipe;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

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
        String senhaTemporaria
) {
}
