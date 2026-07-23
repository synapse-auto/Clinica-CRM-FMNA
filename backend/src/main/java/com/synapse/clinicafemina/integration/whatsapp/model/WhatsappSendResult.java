package com.synapse.clinicafemina.integration.whatsapp.model;

import com.synapse.clinicafemina.integration.whatsapp.WhatsappProviderType;

/**
 * Resultado interno e agnóstico de um envio de mensagem de WhatsApp.
 *
 * @param externalMessageId identificador atribuído pelo provider ao aceitar a mensagem
 *                          (Meta: {@code wamid}; UAZAP: {@code messageId}). Persistido em
 *                          {@code mensagem.whatsapp_message_id} (UNIQUE) para idempotência.
 * @param provider          provider que efetivamente realizou o envio.
 */
public record WhatsappSendResult(String externalMessageId, WhatsappProviderType provider) {

    public WhatsappSendResult {
        if (externalMessageId == null || externalMessageId.isBlank()) {
            throw new IllegalArgumentException("externalMessageId não pode ser vazio");
        }
        if (provider == null) {
            throw new IllegalArgumentException("provider não pode ser nulo");
        }
    }
}
