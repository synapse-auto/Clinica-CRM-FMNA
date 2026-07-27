package com.synapse.clinicafemina.dto;

import com.synapse.clinicafemina.integration.whatsapp.WhatsappProviderType;

/** Capabilities exposed to the protected atendimento UI for the active WhatsApp provider. */
public record WhatsappCapabilitiesDTO(
        WhatsappProviderType provider,
        boolean enforcesCustomerCareWindow,
        boolean supportsMessageTemplates
) {

    public static WhatsappCapabilitiesDTO forProvider(WhatsappProviderType provider) {
        return new WhatsappCapabilitiesDTO(
                provider,
                provider.enforcesCustomerCareWindow(),
                provider.supportsMessageTemplates()
        );
    }
}
