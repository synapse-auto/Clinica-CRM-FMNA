package com.synapse.clinicafemina.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.clinicafemina.domain.Agendamento;
import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Resolves the external professional preserved in an imported appointment payload. */
@Component
@RequiredArgsConstructor
public class AgendaExternalDoctorResolver {

    private final ObjectMapper objectMapper;

    public Optional<ExternalDoctor> resolve(Agendamento agendamento) {
        if (agendamento == null
                || agendamento.getExternalSource() != ExternalProviderType.MEDWARE
                || agendamento.getExternalPayload() == null
                || agendamento.getExternalPayload().isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(agendamento.getExternalPayload());
            JsonNode medware = root.path("medware");
            JsonNode medico = root.path("medico");
            String codigo = firstText(root, medware, medico,
                    "codMedico", "codmedico", "idMedico");
            String nome = firstText(root, medware, medico,
                    "medicoNome", "nomeMedico", "nome", "medico");
            if (nome == null || nome.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new ExternalDoctor(blankToNull(codigo), nome.trim()));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public String key(ExternalDoctor doctor) {
        if (doctor.codigoExterno() != null) {
            return "MEDWARE:" + doctor.codigoExterno();
        }
        return "MEDWARE:NOME:" + normalize(doctor.nome());
    }

    public String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private String firstText(JsonNode root, JsonNode medware, JsonNode medico, String... fields) {
        for (String field : fields) {
            for (JsonNode node : List.of(root, medware, medico)) {
                if (node != null && node.hasNonNull(field)) {
                    String value = node.get(field).asText(null);
                    if (value != null && !value.isBlank()) {
                        return value;
                    }
                }
            }
        }
        return null;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record ExternalDoctor(String codigoExterno, String nome) {
    }
}
