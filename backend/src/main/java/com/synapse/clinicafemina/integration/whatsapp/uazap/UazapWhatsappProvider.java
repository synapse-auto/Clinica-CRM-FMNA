package com.synapse.clinicafemina.integration.whatsapp.uazap;

import com.synapse.clinicafemina.integration.whatsapp.WhatsappProvider;
import com.synapse.clinicafemina.integration.whatsapp.WhatsappProviderType;
import com.synapse.clinicafemina.integration.whatsapp.model.WhatsappMessageType;
import com.synapse.clinicafemina.integration.whatsapp.model.WhatsappSendResult;
import org.springframework.stereotype.Component;

/**
 * Adaptador do provider UAZAP para a abstração {@link WhatsappProvider}.
 * Delega o transporte HTTP ao {@link UazapClient}, mantendo o resto do sistema agnóstico ao provider.
 */
@Component
public class UazapWhatsappProvider implements WhatsappProvider {

    private final UazapClient uazapClient;

    public UazapWhatsappProvider(UazapClient uazapClient) {
        this.uazapClient = uazapClient;
    }

    @Override
    public WhatsappProviderType getType() {
        return WhatsappProviderType.UAZAP;
    }

    @Override
    public WhatsappSendResult sendText(String toE164, String body) {
        return uazapClient.sendText(toE164, body);
    }

    @Override
    public WhatsappSendResult sendMedia(String toE164, WhatsappMessageType type, String mediaReference, String caption) {
        return uazapClient.sendMedia(toE164, type, mediaReference, caption);
    }
}
