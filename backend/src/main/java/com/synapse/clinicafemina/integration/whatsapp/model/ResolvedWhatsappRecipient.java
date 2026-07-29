package com.synapse.clinicafemina.integration.whatsapp.model;

import com.synapse.clinicafemina.integration.whatsapp.WhatsappProvider;

public record ResolvedWhatsappRecipient(
        WhatsappProvider provider,
        String recipient,
        String source,
        boolean providerConfirmed
) {
}
