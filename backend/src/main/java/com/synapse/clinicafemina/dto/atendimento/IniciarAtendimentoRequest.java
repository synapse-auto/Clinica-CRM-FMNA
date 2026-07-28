package com.synapse.clinicafemina.dto.atendimento;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;

public record IniciarAtendimentoRequest(
        @Positive(message = "pacienteId deve ser positivo.")
        Long pacienteId,
        String telefone
) {
    @AssertTrue(message = "Informe exatamente um pacienteId ou telefone.")
    public boolean isOrigemValida() {
        boolean temPaciente = pacienteId != null;
        boolean temTelefone = telefone != null && !telefone.isBlank();
        return temPaciente ^ temTelefone;
    }
}
