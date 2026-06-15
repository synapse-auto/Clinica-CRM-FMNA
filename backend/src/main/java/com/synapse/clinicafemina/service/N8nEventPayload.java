package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.integration.external.ExternalProviderType;

public record N8nEventPayload(
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
    public N8nPublicPayload semConfiguracaoInterna() {
        return new N8nPublicPayload(
                clinicaId,
                clinicaSlug,
                externalProvider,
                evento,
                pacienteId,
                atendimentoId,
                agendamentoId,
                telefone
        );
    }

    public record N8nPublicPayload(
            Long clinicaId,
            String clinicaSlug,
            ExternalProviderType externalProvider,
            String evento,
            Long pacienteId,
            Long atendimentoId,
            Long agendamentoId,
            String telefone
    ) {
    }
}
