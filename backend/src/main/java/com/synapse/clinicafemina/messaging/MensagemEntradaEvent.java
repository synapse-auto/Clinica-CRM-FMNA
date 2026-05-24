package com.synapse.clinicafemina.messaging;

import java.time.OffsetDateTime;

/**
 * Evento publicado no exchange {@code mensagem.entrada} do RabbitMQ
 * quando o webhook da Meta entrega uma nova mensagem de um paciente.
 *
 * Consumidores:
 *  - {@code RealtimeBroadcastService} → push STOMP para o atendente responsável
 *  - N8N (consumer externo) → automações de IA / follow-up
 */
public record MensagemEntradaEvent(
        Long atendimentoId,
        Long clinicaId,
        Long pacienteId,
        String pacienteNomeBusca,
        Long mensagemId,
        String tipoMedia,
        String conteudoPrevia,
        OffsetDateTime dataHora,
        Integer naoLidas,
        Long atendenteResponsavelId
) {}
