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
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
public class N8nEventService {

    private final RestClient restClient;

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

    private OffsetDateTime criadoEmEvento(Mensagem mensagem) {
        if (mensagem.getCriadoEm() != null) {
            return mensagem.getCriadoEm();
        }
        return mensagem.getDataHora();
    }
}
