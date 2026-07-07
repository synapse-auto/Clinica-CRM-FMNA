package com.synapse.clinicafemina.dto;

import com.synapse.clinicafemina.dto.operacional.TagResponse;
import java.time.OffsetDateTime;
import java.util.List;

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
        String ultimaMensagemPrevia,
        Boolean requerRevisao,
        String convenioStatus,
        PacienteResumoDTO paciente,
        AtendenteDTO atendentePrincipal,
        List<TagResponse> tags
) {
    public record PacienteResumoDTO(Long id, String nomeBusca, String telefoneNormalizado) {}
    public record AtendenteDTO(Long id, String nome) {}
}
