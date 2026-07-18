package com.synapse.clinicafemina.dto.paciente;

import java.util.List;

public record PacientePageResponse(
        List<PacienteResumoDTO> content,
        int number,
        int size,
        long totalElements,
        int totalPages,
        PacienteStatusCountsDTO counts
) {
}
