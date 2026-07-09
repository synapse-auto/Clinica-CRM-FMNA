package com.synapse.clinicafemina.dto.paciente;

import com.synapse.clinicafemina.dto.operacional.TagResponse;
import java.time.OffsetDateTime;
import java.util.List;

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
        String fotoUrl,
        OffsetDateTime criadoEm,
        OffsetDateTime ultimaInteracaoEm,
        List<TagResponse> tags
) {
}
