package com.synapse.clinicafemina.integration;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.service.N8nEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Único ponto onde a chamada HTTP ao N8N de fato acontece para mensagens inbound.
 *
 * <p>{@code @TransactionalEventListener(phase = AFTER_COMMIT)} é uma garantia do próprio Spring:
 * este método só é invocado se a transação que publicou o evento tiver efetivamente commitado.
 * Se a transação sofrer rollback, o listener NUNCA roda — não é um try/catch nosso simulando
 * isso, é o mecanismo de sincronização de transação do Spring. {@code @Async} garante que a
 * chamada HTTP roda fora da thread do webhook/listener RabbitMQ.</p>
 *
 * <p>A {@link Clinica} usada aqui é uma instância transitória (nunca tocou um contexto de
 * persistência, nunca é um proxy lazy) construída só com os 4 campos escalares necessários para
 * {@link N8nEventService#enviarPayloadMetaOriginal}.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class N8nMensagemRecebidaEventListener {

    private final N8nEventService n8nEventService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoConfirmarMensagem(N8nMensagemRecebidaEvent event) {
        try {
            if (event.payloadMetaOriginal() != null && event.payloadMetaOriginal().length > 0) {
                n8nEventService.enviarPayloadMetaOriginal(
                        clinicaTransitoria(event), event.payloadMetaOriginal(), event.contexto());
                return;
            }
            if (event.payloadFallback() != null) {
                n8nEventService.emitir(event.payloadFallback());
            }
        } catch (Exception exception) {
            log.warn(
                    "Falha nao tratada ao emitir evento N8N pos-commit; mensagem ja persistida permanece intacta. clinicaId={}, tipoErro={}",
                    event.clinicaId(), exception.getClass().getSimpleName());
        }
    }

    /** Instância transitória (nunca persistida/detached) — só os 4 campos escalares necessários. */
    private Clinica clinicaTransitoria(N8nMensagemRecebidaEvent event) {
        Clinica clinica = new Clinica();
        clinica.setId(event.clinicaId());
        clinica.setSlug(event.clinicaSlug());
        clinica.setUsaN8n(event.clinicaUsaN8n());
        clinica.setN8nWebhookUrl(event.clinicaN8nWebhookUrl());
        return clinica;
    }
}
