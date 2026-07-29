package com.synapse.clinicafemina.dto.atendimento;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record IniciarAtendimentoRequest(
        @Positive(message = "pacienteId deve ser positivo.")
        Long pacienteId,
        String telefone,
        @Size(max = 120, message = "nome deve ter no maximo 120 caracteres.")
        String nome
) {
    @AssertTrue(message = "Informe exatamente um pacienteId ou telefone.")
    public boolean isOrigemValida() {
        boolean temPaciente = pacienteId != null;
        boolean temTelefone = telefone != null && !telefone.isBlank();
        return temPaciente ^ temTelefone;
    }

    @AssertTrue(message = "Nome do contato e obrigatorio quando o telefone for informado.")
    public boolean isNomeValidoParaTelefone() {
        return pacienteId != null || (nome != null && !nome.isBlank());
    }
}
