package com.synapse.clinicafemina.integration.whatsapp;

import com.synapse.clinicafemina.integration.whatsapp.model.WhatsappMessageType;
import com.synapse.clinicafemina.integration.whatsapp.model.WhatsappSendResult;

/**
 * Abstração de envio de mensagens de WhatsApp, independente do provider concreto.
 *
 * <p>Implementações: {@code MetaWhatsappProvider} (Meta Cloud API) e {@code UazapWhatsappProvider}.
 * O provider ativo é escolhido por {@link WhatsappProviderResolver} a partir de
 * {@code app.whatsapp.provider}. Services/controllers dependem apenas desta interface — nunca
 * de {@code if/else} por provider.</p>
 */
public interface WhatsappProvider {

    /** Identifica qual provider esta implementação atende. */
    WhatsappProviderType getType();

    /**
     * Envia uma mensagem de texto simples.
     *
     * @param toE164 destinatário (dígitos no padrão E.164, sem "+")
     * @param body   corpo da mensagem
     * @return resultado com o {@code externalMessageId} atribuído pelo provider
     */
    WhatsappSendResult sendText(String toE164, String body);

    /**
     * Envia uma mensagem de mídia previamente referenciável pelo provider.
     *
     * @param toE164          destinatário (dígitos no padrão E.164, sem "+")
     * @param type            tipo da mídia (IMAGE, AUDIO, VIDEO, DOCUMENT)
     * @param mediaReference  referência da mídia no vocabulário do provider
     *                        (Meta: {@code media_id}; UAZAP: {@code link} ou {@code id})
     * @param caption         legenda opcional; providers que não suportam devem ignorá-la
     * @return resultado com o {@code externalMessageId} atribuído pelo provider
     */
    WhatsappSendResult sendMedia(String toE164, WhatsappMessageType type, String mediaReference, String caption);
}
