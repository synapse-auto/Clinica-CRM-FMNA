package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Atendimento;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Mensagem;
import com.synapse.clinicafemina.domain.Paciente;
import java.time.Duration;
import java.time.OffsetDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Service
public class N8nEventService {

    private final RestClient restClient;

    public record MetaWebhookContext(
            String evento,
            Long atendimentoId,
            Long pacienteId,
            Long mensagemId,
            String tipoMedia,
            String whatsappMessageId,
            String atendimentoOrigem,
            String atendimentoModo,
            Boolean iaAtiva,
            Boolean dentroHorario,
            String horarioMotivo
    ) {
        public MetaWebhookContext(
                String evento,
                Long atendimentoId,
                Long pacienteId,
                Long mensagemId,
                String tipoMedia,
                String whatsappMessageId
        ) {
            this(evento, atendimentoId, pacienteId, mensagemId, tipoMedia, whatsappMessageId,
                    null, null, null, null, null);
        }

        public MetaWebhookContext(
                String evento,
                Long atendimentoId,
                Long pacienteId,
                Long mensagemId,
                String tipoMedia
        ) {
            this(evento, atendimentoId, pacienteId, mensagemId, tipoMedia,
                    null, null, null, null, null, null);
        }

        public MetaWebhookContext(
                String evento,
                Long atendimentoId,
                Long pacienteId,
                Long mensagemId
        ) {
            this(evento, atendimentoId, pacienteId, mensagemId,
                    null, null, null, null, null, null, null);
        }

        public MetaWebhookContext(
                String evento,
                Long atendimentoId,
                Long pacienteId,
                Long mensagemId,
                String tipoMedia,
                String whatsappMessageId,
                String atendimentoOrigem,
                String atendimentoModo,
                Boolean iaAtiva
        ) {
            this(evento, atendimentoId, pacienteId, mensagemId, tipoMedia, whatsappMessageId,
                    atendimentoOrigem, atendimentoModo, iaAtiva, null, null);
        }
    }

    @Autowired
    public N8nEventService(
            RestClient.Builder restClientBuilder,
            @Value("${app.n8n.timeout-seconds:5}") int timeoutSeconds
    ) {
        this(withTimeout(restClientBuilder, timeoutSeconds).build());
    }

    N8nEventService(RestClient restClient) {
        this.restClient = restClient;
    }

    public void emitir(N8nEventPayload payload) {
        if (!payload.usaN8n()) {
            return;
        }
        if (payload.n8nWebhookUrl() == null || payload.n8nWebhookUrl().isBlank()) {
            log.warn("Evento N8N ignorado por webhook ausente: clinica={}, evento={}",
                    payload.clinicaId(), payload.evento());
            return;
        }

        try {
            restClient.post()
                    .uri(payload.n8nWebhookUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload.semConfiguracaoInterna())
                    .retrieve()
                    .toBodilessEntity();
            log.info("Evento N8N emitido: clinica={}, evento={}, mensagem={}",
                    payload.clinicaId(), payload.evento(), payload.mensagemId());
        } catch (Exception e) {
            log.warn("Falha ao emitir evento N8N: clinica={}, evento={}, tipoErro={}",
                    payload.clinicaId(), payload.evento(), e.getClass().getSimpleName());
        }
    }

    public void enviarPayloadMetaOriginal(
            Clinica clinica,
            byte[] payloadOriginal,
            MetaWebhookContext context
    ) {
        if (!Boolean.TRUE.equals(clinica.getUsaN8n())) {
            log.info(
                    "Payload Meta para N8N ignorado porque a clinica nao usa N8N: clinica={}, evento={}, atendimento={}, paciente={}, mensagem={}, tipoMedia={}, whatsappMessageId={}, atendimentoOrigem={}, atendimentoModo={}, iaAtiva={}, payloadBytes={}",
                    clinica.getId(),
                    context == null ? "desconhecido" : context.evento(),
                    context == null ? null : context.atendimentoId(),
                    context == null ? null : context.pacienteId(),
                    context == null ? null : context.mensagemId(),
                    context == null ? null : context.tipoMedia(),
                    maskId(context == null ? null : context.whatsappMessageId()),
                    context == null ? null : context.atendimentoOrigem(),
                    context == null ? null : context.atendimentoModo(),
                    context == null ? null : context.iaAtiva(),
                    payloadBytes(payloadOriginal));
            return;
        }
        if (clinica.getN8nWebhookUrl() == null || clinica.getN8nWebhookUrl().isBlank()) {
            log.warn(
                    "Payload Meta para N8N ignorado por webhook ausente: clinica={}, evento={}, atendimento={}, paciente={}, mensagem={}, tipoMedia={}, whatsappMessageId={}, atendimentoOrigem={}, atendimentoModo={}, iaAtiva={}, payloadBytes={}",
                    clinica.getId(),
                    context == null ? "desconhecido" : context.evento(),
                    context == null ? null : context.atendimentoId(),
                    context == null ? null : context.pacienteId(),
                    context == null ? null : context.mensagemId(),
                    context == null ? null : context.tipoMedia(),
                    maskId(context == null ? null : context.whatsappMessageId()),
                    context == null ? null : context.atendimentoOrigem(),
                    context == null ? null : context.atendimentoModo(),
                    context == null ? null : context.iaAtiva(),
                    payloadBytes(payloadOriginal));
            return;
        }
        if (payloadOriginal == null || payloadOriginal.length == 0) {
            log.warn(
                    "Payload Meta para N8N ignorado por body ausente: clinica={}, evento={}, atendimento={}, paciente={}, mensagem={}, tipoMedia={}, whatsappMessageId={}, atendimentoOrigem={}, atendimentoModo={}, iaAtiva={}, payloadBytes={}",
                    clinica.getId(),
                    context == null ? "desconhecido" : context.evento(),
                    context == null ? null : context.atendimentoId(),
                    context == null ? null : context.pacienteId(),
                    context == null ? null : context.mensagemId(),
                    context == null ? null : context.tipoMedia(),
                    maskId(context == null ? null : context.whatsappMessageId()),
                    context == null ? null : context.atendimentoOrigem(),
                    context == null ? null : context.atendimentoModo(),
                    context == null ? null : context.iaAtiva(),
                    payloadBytes(payloadOriginal));
            return;
        }

        try {
            var response = restClient.post()
                    .uri(clinica.getN8nWebhookUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> preencherHeadersMeta(headers, clinica, context))
                    .body(payloadOriginal)
                    .retrieve()
                    .toBodilessEntity();
            log.info(
                    "Payload Meta original emitido ao N8N: clinica={}, evento={}, atendimento={}, paciente={}, mensagem={}, tipoMedia={}, whatsappMessageId={}, atendimentoOrigem={}, atendimentoModo={}, iaAtiva={}, payloadBytes={}, statusHttp={}",
                    clinica.getId(),
                    context == null ? "desconhecido" : context.evento(),
                    context == null ? null : context.atendimentoId(),
                    context == null ? null : context.pacienteId(),
                    context == null ? null : context.mensagemId(),
                    context == null ? null : context.tipoMedia(),
                    maskId(context == null ? null : context.whatsappMessageId()),
                    context == null ? null : context.atendimentoOrigem(),
                    context == null ? null : context.atendimentoModo(),
                    context == null ? null : context.iaAtiva(),
                    payloadBytes(payloadOriginal),
                    response.getStatusCode().value());
        } catch (RestClientResponseException e) {
            log.warn(
                    "Falha ao emitir payload Meta original ao N8N: clinica={}, evento={}, atendimento={}, paciente={}, mensagem={}, tipoMedia={}, whatsappMessageId={}, atendimentoOrigem={}, atendimentoModo={}, iaAtiva={}, payloadBytes={}, statusHttp={}, tipoErro={}",
                    clinica.getId(),
                    context == null ? "desconhecido" : context.evento(),
                    context == null ? null : context.atendimentoId(),
                    context == null ? null : context.pacienteId(),
                    context == null ? null : context.mensagemId(),
                    context == null ? null : context.tipoMedia(),
                    maskId(context == null ? null : context.whatsappMessageId()),
                    context == null ? null : context.atendimentoOrigem(),
                    context == null ? null : context.atendimentoModo(),
                    context == null ? null : context.iaAtiva(),
                    payloadBytes(payloadOriginal),
                    e.getStatusCode().value(),
                    e.getClass().getSimpleName());
        } catch (Exception e) {
            log.warn(
                    "Falha ao emitir payload Meta original ao N8N: clinica={}, evento={}, atendimento={}, paciente={}, mensagem={}, tipoMedia={}, whatsappMessageId={}, atendimentoOrigem={}, atendimentoModo={}, iaAtiva={}, payloadBytes={}, tipoErro={}",
                    clinica.getId(),
                    context == null ? "desconhecido" : context.evento(),
                    context == null ? null : context.atendimentoId(),
                    context == null ? null : context.pacienteId(),
                    context == null ? null : context.mensagemId(),
                    context == null ? null : context.tipoMedia(),
                    maskId(context == null ? null : context.whatsappMessageId()),
                    context == null ? null : context.atendimentoOrigem(),
                    context == null ? null : context.atendimentoModo(),
                    context == null ? null : context.iaAtiva(),
                    payloadBytes(payloadOriginal),
                    e.getClass().getSimpleName());
        }
    }

    public N8nEventPayload criarPayload(Clinica clinica, String evento,
                                        Long pacienteId, Long atendimentoId,
                                        Long agendamentoId, String telefone) {
        return new N8nEventPayload(
                clinica.getId(),
                clinica.getSlug(),
                clinica.getExternalProvider(),
                evento,
                pacienteId,
                atendimentoId,
                agendamentoId,
                telefone,
                Boolean.TRUE.equals(clinica.getUsaN8n()),
                clinica.getN8nWebhookUrl()
        );
    }

    public N8nEventPayload criarPayloadMensagemRecebida(
            Clinica clinica,
            Paciente paciente,
            Atendimento atendimento,
            Mensagem mensagem
    ) {
        return new N8nEventPayload(
                clinica.getId(),
                clinica.getSlug(),
                clinica.getExternalProvider(),
                "mensagem_recebida",
                paciente.getId(),
                atendimento.getId(),
                null,
                null,
                mensagem.getId(),
                mensagem.getTipoMedia(),
                mensagem.getDirecao(),
                "WHATSAPP",
                criadoEmEvento(mensagem),
                Boolean.TRUE.equals(clinica.getUsaN8n()),
                clinica.getN8nWebhookUrl()
        );
    }

    private static RestClient.Builder withTimeout(RestClient.Builder builder, int timeoutSeconds) {
        int safeTimeout = Math.max(1, timeoutSeconds);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Duration.ofSeconds(safeTimeout).toMillis());
        requestFactory.setReadTimeout((int) Duration.ofSeconds(safeTimeout).toMillis());
        return builder.requestFactory(requestFactory);
    }

    private void preencherHeadersMeta(HttpHeaders headers, Clinica clinica, MetaWebhookContext context) {
        adicionarHeader(headers, "X-CRM-Event", context == null ? null : context.evento());
        adicionarHeader(headers, "X-CRM-Clinic-Slug", clinica.getSlug());
        adicionarHeader(headers, "X-CRM-Atendimento-Id", context == null ? null : context.atendimentoId());
        adicionarHeader(headers, "X-CRM-Paciente-Id", context == null ? null : context.pacienteId());
        adicionarHeader(headers, "X-CRM-Mensagem-Id", context == null ? null : context.mensagemId());
        adicionarHeader(headers, "X-CRM-Whatsapp-Message-Id", context == null ? null : context.whatsappMessageId());
        adicionarHeader(headers, "X-CRM-Atendimento-Origem", context == null ? null : context.atendimentoOrigem());
        adicionarHeader(headers, "X-CRM-Atendimento-Modo", context == null ? null : context.atendimentoModo());
        adicionarHeader(headers, "X-CRM-Ia-Ativa", context == null ? null : context.iaAtiva());
        adicionarHeader(headers, "X-CRM-Dentro-Horario", context == null ? null : context.dentroHorario());
        adicionarHeader(headers, "X-CRM-Horario-Motivo", context == null ? null : context.horarioMotivo());
    }

    private void adicionarHeader(HttpHeaders headers, String nome, Object valor) {
        if (valor == null) {
            return;
        }
        String texto = String.valueOf(valor);
        if (!texto.isBlank()) {
            headers.set(nome, texto);
        }
    }

    private OffsetDateTime criadoEmEvento(Mensagem mensagem) {
        if (mensagem.getCriadoEm() != null) {
            return mensagem.getCriadoEm();
        }
        return mensagem.getDataHora();
    }

    private String maskId(String id) {
        if (id == null || id.isBlank()) {
            return "vazio";
        }
        String trimmed = id.trim();
        if (trimmed.length() <= 4) {
            return "****";
        }
        return "****" + trimmed.substring(trimmed.length() - 4);
    }

    private int payloadBytes(byte[] payloadOriginal) {
        return payloadOriginal == null ? 0 : payloadOriginal.length;
    }
}
