package com.synapse.clinicafemina.dto.equipe;

import java.util.List;

public record EquipeGrupoResponse(
        String perfil,
        String titulo,
        List<EquipeUsuarioResponse> usuarios
) {
}
