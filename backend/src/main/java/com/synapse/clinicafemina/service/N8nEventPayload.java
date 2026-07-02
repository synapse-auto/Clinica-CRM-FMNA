package com.synapse.clinicafemina.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public record N8nEventPayload(
        Long clinicaId,
        String clinicaSlug,
        ExternalProviderType externalProvider,
        String evento,
        Long pacienteId,
        Long atendimentoId,
        Long agendamentoId,
        String telefone,
        Long mensagemId,
        String tipoMedia,
        String direcao,
        String origem,
        OffsetDateTime criadoEm,
        boolean usaN8n,
        String n8nWebhookUrl
) {
    public N8nEventPayload(
            Long clinicaId,
            String clinicaSlug,
            ExternalProviderType externalProvider,
            String evento,
            Long pacienteId,
            Long atendimentoId,
            Long agendamentoId,
            String telefone,
            boolean usaN8n,
            String n8nWebhookUrl
    ) {
        this(
                clinicaId,
                clinicaSlug,
                externalProvider,
                evento,
                pacienteId,
                atendimentoId,
                agendamentoId,
                telefone,
                null,
                null,
                null,
                null,
                null,
                usaN8n,
                n8nWebhookUrl
        );
    }

    public N8nPublicPayload semConfiguracaoInterna() {
        return new N8nPublicPayload(
                evento,
                clinicaId,
                atendimentoId,
                pacienteId,
                mensagemId,
                tipoMedia,
                direcao,
                origem,
                criadoEm == null ? null : DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(criadoEm),
                clinicaSlug,
                externalProvider,
                agendamentoId,
                telefone
        );
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record N8nPublicPayload(
            String evento,
            Long clinicaId,
            Long atendimentoId,
            Long pacienteId,
            Long mensagemId,
            String tipoMedia,
            String direcao,
            String origem,
            String criadoEm,
            String clinicaSlug,
            ExternalProviderType externalProvider,
            Long agendamentoId,
            String telefone
    ) {
    }
}
