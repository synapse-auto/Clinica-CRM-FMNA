package com.synapse.clinicafemina.dto;

import java.time.OffsetDateTime;

/**
 * DTO de detalhes do atendimento.
 * Retornado no GET /api/atendimentos/{id} — inclui dados completos do paciente
 * (decriptografados pelo AesGcmConverter na camada de persistência).
 */
public record AtendimentoDetalheDTO(
        Long id,
        String status,
        Boolean tratadoPorIa,
        OffsetDateTime dataInicio,
        OffsetDateTime dataEncerramento,
        Integer naoLidas,
        PacienteDetalheDTO paciente,
        AtendenteDTO atendentePrincipal
) {
    public record PacienteDetalheDTO(
            Long id,
            String nome,
            String telefone,
            String email,
            String status,
            OffsetDateTime ultimaInteracaoEm
    ) {}

    public record AtendenteDTO(Long id, String nome, String perfil) {}
}
