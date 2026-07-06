package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.IntegrationSyncLog;
import com.synapse.clinicafemina.dto.configuracao.ConfiguracaoResumoResponse;
import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import com.synapse.clinicafemina.repository.HorarioAtendenteRepository;
import com.synapse.clinicafemina.repository.IntegrationSyncLogRepository;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConfiguracaoResumoService {

    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"));

    private final ClinicaConfigService clinicaConfigService;
    private final IntegrationSyncLogRepository integrationSyncLogRepository;
    private final UsuarioRepository usuarioRepository;
    private final HorarioAtendenteRepository horarioAtendenteRepository;
    private final OffsetDateTime inicializadoEm = OffsetDateTime.now();

    @Value("${app.environment:teste}")
    private String ambiente;

    @Transactional(readOnly = true)
    public ConfiguracaoResumoResponse obterResumo() {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        Long clinicaId = clinica.getId();
        boolean whatsappConfigurado = temTexto(clinica.getWhatsappPhoneNumberId());
        boolean n8nWebhookConfigurado = temTexto(clinica.getN8nWebhookUrl());
        boolean n8nConfigurado = Boolean.TRUE.equals(clinica.getUsaN8n()) && n8nWebhookConfigurado;
        boolean medwareConfigurado = ExternalProviderType.MEDWARE.equals(clinica.getExternalProvider());
        IntegrationSyncLog ultimaSyncMedware = integrationSyncLogRepository
                .findTopByClinicaIdAndExternalProviderOrderByIniciadoEmDesc(clinicaId, ExternalProviderType.MEDWARE)
                .orElse(null);

        return new ConfiguracaoResumoResponse(
                identidade(clinica, whatsappConfigurado, n8nConfigurado),
                integracoes(whatsappConfigurado, n8nConfigurado, medwareConfigurado, ultimaSyncMedware),
                ultimaSincronizacao(ultimaSyncMedware),
                seguranca(clinicaId),
                operacao(clinicaId, n8nConfigurado),
                new ConfiguracaoResumoResponse.Ambiente(ambienteSeguro(), inicializadoEm)
        );
    }

    private ConfiguracaoResumoResponse.Identidade identidade(
            Clinica clinica,
            boolean whatsappConfigurado,
            boolean n8nConfigurado
    ) {
        return new ConfiguracaoResumoResponse.Identidade(
                clinica.getNome(),
                clinica.getSlug(),
                clinica.getTipoClinica(),
                clinica.getExternalProvider() == null ? "NAO_CONFIGURADO" : clinica.getExternalProvider().name(),
                "Operacional",
                whatsappConfigurado,
                n8nConfigurado
        );
    }

    private List<ConfiguracaoResumoResponse.Integracao> integracoes(
            boolean whatsappConfigurado,
            boolean n8nConfigurado,
            boolean medwareConfigurado,
            IntegrationSyncLog ultimaSyncMedware
    ) {
        List<ConfiguracaoResumoResponse.Integracao> itens = new ArrayList<>();
        itens.add(new ConfiguracaoResumoResponse.Integracao(
                "WhatsApp Oficial",
                whatsappConfigurado ? "Configurado" : "Pendente",
                whatsappConfigurado ? "Webhook oficial ligado ao CRM" : "Phone number ID nao configurado"
        ));
        itens.add(new ConfiguracaoResumoResponse.Integracao(
                "N8N",
                n8nConfigurado ? "Configurado" : "Desativado",
                n8nConfigurado ? "Eventos protegidos por cabecalho privado" : "Automacao externa desativada"
        ));
        itens.add(new ConfiguracaoResumoResponse.Integracao(
                "Medware",
                statusMedware(medwareConfigurado, ultimaSyncMedware),
                detalheMedware(medwareConfigurado, ultimaSyncMedware)
        ));
        return itens;
    }

    private String statusMedware(boolean medwareConfigurado, IntegrationSyncLog ultimaSyncMedware) {
        if (!medwareConfigurado) {
            return "Desativado";
        }
        if (ultimaSyncMedware == null) {
            return "Pendente";
        }
        return "SUCESSO".equals(ultimaSyncMedware.getStatus()) ? "Configurado" : "Pendente";
    }

    private String detalheMedware(boolean medwareConfigurado, IntegrationSyncLog ultimaSyncMedware) {
        if (!medwareConfigurado) {
            return "Provider externo nao esta como MEDWARE";
        }
        if (ultimaSyncMedware == null) {
            return "Aguardando primeira sincronizacao";
        }
        return "Ultima sync: " + ultimaSyncMedware.getStatus();
    }

    private ConfiguracaoResumoResponse.UltimaSincronizacao ultimaSincronizacao(IntegrationSyncLog log) {
        if (log == null) {
            return null;
        }
        return new ConfiguracaoResumoResponse.UltimaSincronizacao(
                log.getStatus(),
                log.getIniciadoEm(),
                log.getConcluidoEm(),
                log.getDataInicio() == null ? null : DATA_BR.format(log.getDataInicio()),
                log.getDataFim() == null ? null : DATA_BR.format(log.getDataFim()),
                valorOuZero(log.getPacientesProcessados()),
                valorOuZero(log.getAgendamentosProcessados()),
                valorOuZero(log.getAgendamentosIgnorados()),
                sanitizarErro(log.getMensagemErro())
        );
    }

    private ConfiguracaoResumoResponse.Seguranca seguranca(Long clinicaId) {
        List<ConfiguracaoResumoResponse.PerfilAtivo> perfis = usuarioRepository
                .countAtivosVisiveisPorPerfil(clinicaId)
                .stream()
                .map(item -> new ConfiguracaoResumoResponse.PerfilAtivo(
                        String.valueOf(item[0]),
                        ((Number) item[1]).intValue()
                ))
                .toList();
        return new ConfiguracaoResumoResponse.Seguranca(
                perfis,
                List.of(
                        "Sessao protegida por JWT",
                        "Troca obrigatoria de senha no primeiro acesso",
                        "Logs sem dados sensiveis"
                )
        );
    }

    private ConfiguracaoResumoResponse.Operacao operacao(Long clinicaId, boolean n8nConfigurado) {
        return new ConfiguracaoResumoResponse.Operacao(
                horarioAtendenteRepository.existsAtivoByClinicaId(clinicaId),
                n8nConfigurado,
                true,
                true,
                true
        );
    }

    private String ambienteSeguro() {
        return temTexto(ambiente) ? ambiente.trim() : "teste";
    }

    private String sanitizarErro(String mensagem) {
        if (!temTexto(mensagem)) {
            return null;
        }
        String limpa = mensagem
                .replaceAll("(?i)https?://\\S+", "[url]")
                .replaceAll("(?i)(bearer\\s+)[^\\s]+", "$1[redigido]")
                .replaceAll("(?i)(token|senha|password|secret)=\\S+", "$1=[redigido]")
                .trim();
        return limpa.length() > 180 ? limpa.substring(0, 177) + "..." : limpa;
    }

    private int valorOuZero(Integer valor) {
        return valor == null ? 0 : valor;
    }

    private boolean temTexto(String valor) {
        return valor != null && !valor.isBlank();
    }
}
