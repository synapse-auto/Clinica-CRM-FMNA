package com.synapse.clinicafemina.integration;

import com.synapse.clinicafemina.service.N8nEventPayload;
import com.synapse.clinicafemina.service.N8nEventService;

/**
 * Publicado por {@link WhatsappInboundMapper} após todas as escritas de uma mensagem inbound
 * (paciente, atendimento, mensagem, mídia) dentro da mesma transação por mensagem
 * ({@code REQUIRES_NEW}). Consumido apenas em
 * {@code @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)}
 * ({@link N8nMensagemRecebidaEventListener}) — ou seja, a chamada HTTP ao N8N só acontece depois
 * que o commit desta transação individual já foi confirmado no PostgreSQL.
 *
 * <p><strong>Nunca carrega entidades JPA</strong> (Clinica/Atendimento/Paciente/Mensagem) —
 * apenas valores primitivos e os dois objetos já prontos (records puros, sem proxy) usados para
 * montar a chamada ao N8N. Exatamente um entre ({@code payloadMetaOriginal}+{@code contexto}) e
 * {@code payloadFallback} é não-nulo, espelhando o branch já decidido antes do commit.</p>
 */
public record N8nMensagemRecebidaEvent(
        Long clinicaId,
        String clinicaSlug,
        Boolean clinicaUsaN8n,
        String clinicaN8nWebhookUrl,
        byte[] payloadMetaOriginal,
        N8nEventService.MetaWebhookContext contexto,
        N8nEventPayload payloadFallback
) {
}
