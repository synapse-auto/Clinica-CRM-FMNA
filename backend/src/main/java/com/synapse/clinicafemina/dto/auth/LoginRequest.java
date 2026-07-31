package com.synapse.clinicafemina.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "O email não pode ser vazio")
    @Email(message = "Email inválido")
    private String email;

    @NotBlank(message = "A senha não pode ser vazia")
    @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, format = "password")
    private String senha;
}
