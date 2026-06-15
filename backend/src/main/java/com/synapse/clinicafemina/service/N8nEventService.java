package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Clinica;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
public class N8nEventService {

    private final RestClient restClient;

    public N8nEventService() {
        this.restClient = RestClient.builder().build();
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
        } catch (Exception e) {
            log.warn("Falha ao emitir evento N8N: clinica={}, evento={}",
                    payload.clinicaId(), payload.evento());
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
}
