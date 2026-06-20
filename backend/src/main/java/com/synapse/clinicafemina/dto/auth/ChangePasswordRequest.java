package com.synapse.clinicafemina.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Senha atual é obrigatória")
        String senhaAtual,

        @NotBlank(message = "Nova senha é obrigatória")
        @Size(min = 12, max = 72, message = "Nova senha deve ter entre 12 e 72 caracteres")
        String novaSenha,

        @NotBlank(message = "Confirmação da nova senha é obrigatória")
        String confirmacaoNovaSenha
) {
}
