package com.synapse.clinicafemina.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Senha atual é obrigatória")
        String senhaAtual,

        @NotBlank(message = "Nova senha é obrigatória")
        @Size(max = 72, message = "Nova senha deve ter no máximo 72 caracteres")
        String novaSenha,

        @NotBlank(message = "Confirmação da nova senha é obrigatória")
        String confirmacaoNovaSenha
) {
}
