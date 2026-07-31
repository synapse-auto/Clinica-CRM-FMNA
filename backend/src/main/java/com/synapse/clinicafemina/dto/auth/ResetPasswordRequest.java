package com.synapse.clinicafemina.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public record ResetPasswordRequest(
        @NotBlank(message = "A senha temporária é obrigatória.")
        @Size(max = 72, message = "A senha não pode ter mais que 72 caracteres.")
        @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, format = "password") String senhaTemporaria
) {
}
