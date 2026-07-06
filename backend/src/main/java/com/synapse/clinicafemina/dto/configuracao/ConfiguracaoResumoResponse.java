package com.synapse.clinicafemina.dto.configuracao;

import com.synapse.clinicafemina.domain.TipoClinica;
import java.time.OffsetDateTime;
import java.util.List;

public record ConfiguracaoResumoResponse(
        Identidade identidade,
        List<Integracao> integracoes,
        UltimaSincronizacao ultimaSincronizacaoMedware,
        Seguranca seguranca,
        Operacao operacao,
        Ambiente ambiente
) {
    public record Identidade(
            String nome,
            String slug,
            TipoClinica tipoClinica,
            String externalProvider,
            String statusOperacional,
            boolean whatsappConfigurado,
            boolean n8nConfigurado
    ) {
    }

    public record Integracao(
            String nome,
            String status,
            String detalhe
    ) {
    }

    public record UltimaSincronizacao(
            String status,
            OffsetDateTime iniciadoEm,
            OffsetDateTime concluidoEm,
            String dataInicio,
            String dataFim,
            int pacientesProcessados,
            int agendamentosProcessados,
            int agendamentosIgnorados,
            String erroResumo
    ) {
    }

    public record Seguranca(
            List<PerfilAtivo> perfisAtivos,
            List<String> regras
    ) {
    }

    public record PerfilAtivo(
            String perfil,
            int total
    ) {
    }

    public record Operacao(
            boolean horariosConfigurados,
            boolean iaAtiva,
            boolean retornoHumanoIa24h,
            boolean agendaMedicoSomenteLeitura,
            boolean mutacaoAgendaRestrita
    ) {
    }

    public record Ambiente(
            String nome,
            OffsetDateTime inicializadoEm
    ) {
    }
}
