package com.synapse.clinicafemina.dto.equipe;

import java.time.OffsetDateTime;

public record EquipeUsuarioResponse(
        Long id,
        String nome,
        String email,
        String telefone,
        String perfil,
        Boolean ativo,
        Boolean mustChangePassword,
        Boolean podeGerenciarUsuarios,
        OffsetDateTime criadoEm
) {
}
