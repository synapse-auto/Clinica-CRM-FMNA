package com.synapse.clinicafemina.integration.whatsapp.uazap;

import com.synapse.clinicafemina.integration.whatsapp.WhatsappProvider;
import com.synapse.clinicafemina.integration.whatsapp.WhatsappProviderType;
import com.synapse.clinicafemina.integration.whatsapp.WhatsappPhoneNormalizer;
import com.synapse.clinicafemina.integration.whatsapp.WhatsappRecipientResolutionException;
import com.synapse.clinicafemina.integration.whatsapp.model.WhatsappMessageType;
import com.synapse.clinicafemina.integration.whatsapp.model.WhatsappRecipientResolution;
import com.synapse.clinicafemina.integration.whatsapp.model.WhatsappSendResult;
import org.springframework.stereotype.Component;
import org.springframework.core.io.Resource;

import java.util.Set;

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
    public WhatsappRecipientResolution resolveRecipient(
            String confirmedChatId,
            String registeredPhone,
            Set<String> safeAliases
    ) {
        if (confirmedChatId != null && !confirmedChatId.isBlank()) {
            String normalizedChatId = safeAliases.contains(confirmedChatId)
                    ? confirmedChatId
                    : WhatsappPhoneNormalizer.normalize(confirmedChatId);
            if (!safeAliases.contains(normalizedChatId)) {
                throw new WhatsappRecipientResolutionException(
                        "Identidade WhatsApp ambígua. O envio não foi realizado."
                );
            }
            return new WhatsappRecipientResolution(
                    normalizedChatId, true, "WHATSAPP_CHAT_ID"
            );
        }
        return new WhatsappRecipientResolution(
                safeAliases.contains(registeredPhone)
                        ? registeredPhone
                        : WhatsappPhoneNormalizer.normalize(registeredPhone),
                false,
                "TELEFONE_CADASTRAL"
        );
    }

    @Override
    public WhatsappSendResult sendText(String toE164, String body) {
        return uazapClient.sendText(toE164, body);
    }

    @Override
    public WhatsappSendResult sendMedia(String toE164, WhatsappMessageType type, String mediaReference, String caption) {
        return uazapClient.sendMedia(toE164, type, mediaReference, caption);
    }

    @Override
    public String uploadMedia(Resource recurso, String contentType, String nomeArquivo) {
        return uazapClient.uploadMedia(recurso, contentType, nomeArquivo);
    }
}
