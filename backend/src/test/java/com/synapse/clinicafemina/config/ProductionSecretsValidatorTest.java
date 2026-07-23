package com.synapse.clinicafemina.config;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.integration.whatsapp.config.WhatsappProperties;
import com.synapse.clinicafemina.repository.ClinicaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductionSecretsValidator — exigência de secrets condicional por provider de WhatsApp")
class ProductionSecretsValidatorTest {

    @Mock
    private Environment environment;

    @Mock
    private ClinicaRepository clinicaRepository;

    private final Map<String, String> props = new HashMap<>();

    @BeforeEach
    void setUp() {
        lenient().when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        lenient().when(environment.getProperty(anyString()))
                .thenAnswer(invocation -> props.get(invocation.<String>getArgument(0)));

        // Secrets base sempre presentes para isolar a lógica de WhatsApp.
        props.put("app.clinic.slug", "clinica-teste");
        props.put("spring.datasource.url", "jdbc:postgresql://db/x");
        props.put("spring.datasource.username", "u");
        props.put("spring.datasource.password", "p");
        props.put("app.security.jwt.secret", "s");
        props.put("app.crypto.master-key", "k");

        // getExternalProvider() nulo → sem exigência Darwin/Medware, isolando a lógica de WhatsApp.
        Clinica clinica = mock(Clinica.class);
        lenient().when(clinicaRepository.findBySlug("clinica-teste")).thenReturn(Optional.of(clinica));
    }

    private ProductionSecretsValidator validatorWith(WhatsappProperties whatsapp) {
        return new ProductionSecretsValidator(environment, clinicaRepository, whatsapp);
    }

    private WhatsappProperties whatsapp(boolean enabled, String provider) {
        WhatsappProperties whatsapp = new WhatsappProperties();
        whatsapp.setEnabled(enabled);
        whatsapp.setProvider(provider);
        return whatsapp;
    }

    @Test
    @DisplayName("WHATSAPP_ENABLED=false não exige credenciais Meta nem UAZAP")
    void disabled_requiresNoWhatsappCredentials() {
        assertThatCode(() -> validatorWith(whatsapp(false, "META")).run(null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("provider META habilitado com credenciais Meta presentes passa")
    void metaEnabled_withMetaSecrets_passes() {
        props.put("app.whatsapp.verify-token", "vt");
        props.put("app.whatsapp.app-secret", "as");
        props.put("app.whatsapp.access-token", "at");
        props.put("app.whatsapp.phone-number-id", "pnid");

        assertThatCode(() -> validatorWith(whatsapp(true, "META")).run(null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("provider META habilitado sem credenciais Meta falha listando as chaves Meta")
    void metaEnabled_withoutMetaSecrets_fails() {
        assertThatThrownBy(() -> validatorWith(whatsapp(true, "META")).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.whatsapp.access-token");
    }

    @Test
    @DisplayName("provider UAZAP habilitado exige apenas credenciais UAZAP (Meta ausente é aceito)")
    void uazapEnabled_withUazapSecrets_passesWithoutMeta() {
        props.put("app.whatsapp.uazap.base-url", "https://api.uzapi.com.br");
        props.put("app.whatsapp.uazap.username", "user");
        props.put("app.whatsapp.uazap.version", "v2");
        props.put("app.whatsapp.uazap.phone-number-id", "inst-1");
        props.put("app.whatsapp.uazap.token", "tok");

        assertThatCode(() -> validatorWith(whatsapp(true, "UAZAP")).run(null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("provider UAZAP habilitado sem token falha listando a chave UAZAP")
    void uazapEnabled_withoutToken_fails() {
        props.put("app.whatsapp.uazap.base-url", "https://api.uzapi.com.br");
        props.put("app.whatsapp.uazap.username", "user");
        props.put("app.whatsapp.uazap.version", "v2");
        props.put("app.whatsapp.uazap.phone-number-id", "inst-1");
        // token ausente

        assertThatThrownBy(() -> validatorWith(whatsapp(true, "UAZAP")).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.whatsapp.uazap.token");
    }

    @Test
    @DisplayName("WHATSAPP_PROVIDER inválido falha com mensagem clara")
    void invalidProvider_failsClearly() {
        assertThatThrownBy(() -> validatorWith(whatsapp(true, "EVOLUTION")).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WHATSAPP_PROVIDER inválido");
    }
}
