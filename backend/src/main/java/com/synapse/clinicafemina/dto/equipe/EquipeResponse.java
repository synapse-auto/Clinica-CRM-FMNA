package com.synapse.clinicafemina.dto.equipe;

import java.util.List;

public record EquipeResponse(
        List<EquipeGrupoResponse> grupos
) {
}
