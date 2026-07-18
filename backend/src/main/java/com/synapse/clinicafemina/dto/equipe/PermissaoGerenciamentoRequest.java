package com.synapse.clinicafemina.dto.equipe;

import jakarta.validation.constraints.NotNull;

public record PermissaoGerenciamentoRequest(
        @NotNull(message = "A permissão deve ser informada.")
        Boolean podeGerenciarUsuarios
) {
}
