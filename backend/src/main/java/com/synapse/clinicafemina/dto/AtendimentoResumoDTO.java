package com.synapse.clinicafemina.dto;

import java.time.OffsetDateTime;

/**
 * DTO resumido usado na listagem de atendimentos.
 * Não expõe dados PII descriptografados — apenas campos de busca/index.
 */
public record AtendimentoResumoDTO(
        Long id,
        String status,
        Boolean tratadoPorIa,
        OffsetDateTime ultimaMensagemEm,
        Integer naoLidas,
        PacienteResumoDTO paciente,
        AtendenteDTO atendentePrincipal
) {
    public record PacienteResumoDTO(Long id, String nomeBusca, String telefoneNormalizado) {}
    public record AtendenteDTO(Long id, String nome) {}
}
