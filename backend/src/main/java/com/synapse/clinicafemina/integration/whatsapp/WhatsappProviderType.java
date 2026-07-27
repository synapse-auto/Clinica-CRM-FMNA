package com.synapse.clinicafemina.integration.whatsapp;

/**
 * Providers de WhatsApp suportados pelo backend.
 *
 * <p>Selecionado por {@code app.whatsapp.provider} (variável {@code WHATSAPP_PROVIDER}).
 * É <strong>independente</strong> do provider clínico
 * ({@code EXTERNAL_PROVIDER} / Darwin / Medware, ver
 * {@link com.synapse.clinicafemina.integration.external.ExternalProviderType}).
 * Os dois conceitos nunca devem ser misturados: UAZAP é apenas provider de WhatsApp,
 * jamais um provider clínico.</p>
 */
public enum WhatsappProviderType {
    META,
    UAZAP;

    /** Meta enforces the customer-care window; UAZAP does not expose that rule here. */
    public boolean enforcesCustomerCareWindow() {
        return this == META;
    }

    /** Templates are a Meta Cloud API capability, not a generic WhatsApp capability. */
    public boolean supportsMessageTemplates() {
        return this == META;
    }
}
