package com.synapse.clinicafemina.integration.whatsapp.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WhatsappPropertiesTest {

    @Test
    void pictureEnrichmentIsEnabledByDefault() {
        WhatsappProperties properties = new WhatsappProperties();

        assertThat(properties.getUazap().isPictureEnrichmentEnabled()).isTrue();
    }

    @Test
    void bindsPictureEnrichmentAsFalse() {
        assertThat(bind("false").getUazap().isPictureEnrichmentEnabled()).isFalse();
    }

    @Test
    void bindsPictureEnrichmentAsTrue() {
        assertThat(bind("true").getUazap().isPictureEnrichmentEnabled()).isTrue();
    }

    private WhatsappProperties bind(String value) {
        var source = new MapConfigurationPropertySource(
                Map.of("app.whatsapp.uazap.picture-enrichment-enabled", value));
        return new Binder(source)
                .bind("app.whatsapp", Bindable.of(WhatsappProperties.class))
                .orElseThrow(() -> new IllegalStateException("Falha no binding de teste"));
    }
}
