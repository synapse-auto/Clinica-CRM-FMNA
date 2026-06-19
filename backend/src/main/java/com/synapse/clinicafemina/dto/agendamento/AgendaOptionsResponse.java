package com.synapse.clinicafemina.dto.agendamento;

import java.util.List;

public record AgendaOptionsResponse(
        List<AgendaOptionResponse> pacientes,
        List<AgendaOptionResponse> medicos
) {
}
