package com.synapse.clinicafemina.integration.whatsapp;

import com.synapse.clinicafemina.integration.whatsapp.config.WhatsappProperties;
import com.synapse.clinicafemina.integration.whatsapp.model.WhatsappMessageType;
import com.synapse.clinicafemina.integration.whatsapp.model.WhatsappSendResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("WhatsappProviderResolver — seleção centralizada por app.whatsapp.provider")
class WhatsappProviderResolverTest {

    /** Dublê mínimo de provider, identificado apenas pelo tipo. */
    private static WhatsappProvider fakeProvider(WhatsappProviderType type) {
        return new WhatsappProvider() {
            @Override
            public WhatsappProviderType getType() {
                return type;
            }

            @Override
            public WhatsappSendResult sendText(String toE164, String body) {
                return new WhatsappSendResult("id", type);
            }

            @Override
            public WhatsappSendResult sendMedia(String toE164, WhatsappMessageType t, String ref, String caption) {
                return new WhatsappSendResult("id", type);
            }
        };
    }

    private WhatsappProviderResolver resolverWithProvider(String configured) {
        WhatsappProperties properties = new WhatsappProperties();
        properties.setProvider(configured);
        return new WhatsappProviderResolver(
                List.of(fakeProvider(WhatsappProviderType.META), fakeProvider(WhatsappProviderType.UAZAP)),
                properties);
    }

    @Test
    @DisplayName("provider META resolve para o provider do tipo META")
    void resolvesMeta() {
        WhatsappProvider resolved = resolverWithProvider("META").resolve();
        assertThat(resolved.getType()).isEqualTo(WhatsappProviderType.META);
    }

    @Test
    @DisplayName("provider UAZAP resolve para o provider do tipo UAZAP")
    void resolvesUazap() {
        WhatsappProvider resolved = resolverWithProvider("UAZAP").resolve();
        assertThat(resolved.getType()).isEqualTo(WhatsappProviderType.UAZAP);
    }

    @Test
    @DisplayName("tipo não registrado falha com WhatsappProviderException clara")
    void unavailableProvider_throws() {
        WhatsappProperties properties = new WhatsappProperties();
        properties.setProvider("UAZAP");
        // Apenas META registrado.
        WhatsappProviderResolver resolver = new WhatsappProviderResolver(
                List.of(fakeProvider(WhatsappProviderType.META)), properties);

        assertThatThrownBy(resolver::resolve)
                .isInstanceOf(WhatsappProviderException.class)
                .hasMessageContaining("não disponível");
    }

    @Test
    @DisplayName("provider inválido em config falha com mensagem clara")
    void invalidProvider_throws() {
        assertThatThrownBy(() -> resolverWithProvider("EVOLUTION").resolve())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WHATSAPP_PROVIDER inválido");
    }
}
