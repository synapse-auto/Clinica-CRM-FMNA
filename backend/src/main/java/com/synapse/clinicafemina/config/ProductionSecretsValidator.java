package com.synapse.clinicafemina.config;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import com.synapse.clinicafemina.integration.whatsapp.WhatsappProviderType;
import com.synapse.clinicafemina.integration.whatsapp.config.WhatsappProperties;
import com.synapse.clinicafemina.repository.ClinicaRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

@Component
public class ProductionSecretsValidator implements ApplicationRunner {

    private final Environment environment;
    private final ClinicaRepository clinicaRepository;
    private final WhatsappProperties whatsappProperties;

    public ProductionSecretsValidator(Environment environment,
                                      ClinicaRepository clinicaRepository,
                                      WhatsappProperties whatsappProperties) {
        this.environment = environment;
        this.clinicaRepository = clinicaRepository;
        this.whatsappProperties = whatsappProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean prod = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (!prod) {
            return;
        }

        List<String> required = new ArrayList<>(List.of(
                "app.clinic.slug",
                "spring.datasource.url",
                "spring.datasource.username",
                "spring.datasource.password",
                "app.security.jwt.secret",
                "app.crypto.master-key"
        ));

        if (whatsappProperties.isEnabled()) {
            // resolveProvider() lança IllegalStateException com mensagem clara se WHATSAPP_PROVIDER for inválido.
            WhatsappProviderType whatsappProvider = whatsappProperties.resolveProvider();
            if (whatsappProvider == WhatsappProviderType.META) {
                required.addAll(List.of(
                        "app.whatsapp.verify-token",
                        "app.whatsapp.app-secret",
                        "app.whatsapp.access-token",
                        "app.whatsapp.phone-number-id"
                ));
            } else if (whatsappProvider == WhatsappProviderType.UAZAP) {
                // webhook-secret é opcional (o painel UAZAP ainda não confirmou suporte a segredo na URL).
                required.addAll(List.of(
                        "app.whatsapp.uazap.base-url",
                        "app.whatsapp.uazap.username",
                        "app.whatsapp.uazap.version",
                        "app.whatsapp.uazap.phone-number-id",
                        "app.whatsapp.uazap.token"
                ));
            }
        }

        String clinicSlug = environment.getProperty("app.clinic.slug");
        if (clinicSlug != null && !clinicSlug.isBlank()) {
            Clinica clinica = clinicaRepository.findBySlug(clinicSlug)
                    .orElseThrow(() -> new IllegalStateException("CLINIC_SLUG não corresponde a uma clínica cadastrada"));
            if (clinica.getExternalProvider() == ExternalProviderType.DARWIN) {
                required.add("app.darwin.api-url");
                required.add("app.darwin.api-token");
            }
            if (clinica.getExternalProvider() == ExternalProviderType.MEDWARE) {
                required.add("app.medware.api-url");
                required.add("app.medware.username");
                required.add("app.medware.password");
            }
        }

        List<String> missing = required.stream()
                .filter(key -> {
                    String value = environment.getProperty(key);
                    return value == null || value.isBlank();
                })
                .toList();

        if (!missing.isEmpty()) {
            throw new IllegalStateException("Configuração de produção incompleta. Variáveis ausentes: " + missing);
        }
    }
}
