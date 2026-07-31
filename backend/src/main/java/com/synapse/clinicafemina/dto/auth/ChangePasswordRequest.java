package com.synapse.clinicafemina.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public record ChangePasswordRequest(
        @NotBlank(message = "Senha atual é obrigatória")
        @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, format = "password") String senhaAtual,

        @NotBlank(message = "Nova senha é obrigatória")
        @Size(max = 72, message = "Nova senha deve ter no máximo 72 caracteres")
        @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, format = "password") String novaSenha,

        @NotBlank(message = "Confirmação da nova senha é obrigatória")
        @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, format = "password") String confirmacaoNovaSenha
) {
}
