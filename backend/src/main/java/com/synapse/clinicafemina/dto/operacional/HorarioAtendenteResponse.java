package com.synapse.clinicafemina.dto.operacional;

import java.time.LocalTime;
import java.time.OffsetDateTime;

public record HorarioAtendenteResponse(
        Long id,
        Long usuarioId,
        String usuarioNome,
        Integer diaSemana,
        LocalTime horaInicio,
        LocalTime horaFim,
        boolean ativo,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm
) {
}
