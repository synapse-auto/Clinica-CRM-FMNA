package com.synapse.clinicafemina.dto.paciente;

import java.time.OffsetDateTime;

/**
 * DTO de listagem de pacientes — retorna apenas dados não-sensíveis.
 * Campos criptografados (CPF, e-mail, endereço, notas) são omitidos na listagem geral.
 */
public record PacienteResumoDTO(
        Long id,
        String nome,
        String telefone,
        String status,
        String externalSource,
        String externalId,
        OffsetDateTime criadoEm,
        OffsetDateTime ultimaInteracaoEm
) {
}
