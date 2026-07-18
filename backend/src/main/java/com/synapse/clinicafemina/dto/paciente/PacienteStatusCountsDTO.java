package com.synapse.clinicafemina.dto.paciente;

public record PacienteStatusCountsDTO(
        long total,
        long emAtendimento,
        long agendado,
        long finalizado,
        long outros
) {
}
