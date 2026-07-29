package com.synapse.clinicafemina.integration.whatsapp.model;

public record WhatsappRecipientResolution(
        String recipient,
        boolean providerConfirmed,
        String source
) {
}
