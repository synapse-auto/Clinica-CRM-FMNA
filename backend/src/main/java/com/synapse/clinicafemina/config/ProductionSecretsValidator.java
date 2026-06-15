package com.synapse.clinicafemina.config;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.integration.external.ExternalProviderType;
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

    public ProductionSecretsValidator(Environment environment, ClinicaRepository clinicaRepository) {
        this.environment = environment;
        this.clinicaRepository = clinicaRepository;
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
                "app.crypto.master-key",
                "app.whatsapp.verify-token",
                "app.whatsapp.app-secret",
                "app.whatsapp.access-token",
                "app.whatsapp.phone-number-id"
        ));

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
                required.add("app.medware.api-token");
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
