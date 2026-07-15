package com.synapse.clinicafemina.integration.external;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MedwareApiMapper {

    private static final ZoneId MEDWARE_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter BR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter BR_DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm[:ss]");
    private static final String UNKNOWN_ENVELOPE_MESSAGE =
            "Resposta Medware com envelope nao reconhecido.";

    private final ObjectMapper objectMapper;

    public List<JsonNode> extractItems(JsonNode response) {
        if (response == null || response.isNull() || response.isMissingNode()) {
            return List.of();
        }
        if (response.isArray()) {
            return toList(response);
        }
        for (String field : List.of("data", "dados", "items", "itens", "lista", "result", "resultado", "value")) {
            JsonNode child = find(response, field);
            if (child != null && (child.isNull() || child.isMissingNode())) {
                return List.of();
            }
            if (child != null && child.isArray()) {
                return toList(child);
            }
        }
        if (response.isObject() && response.isEmpty()) {
            return List.of();
        }
        throw new IllegalStateException(UNKNOWN_ENVELOPE_MESSAGE);
    }

    public ExternalPatientDTO toPatient(JsonNode node) {
        return new ExternalPatientDTO(
                text(node, "codPaciente", "codpaciente", "idPaciente", "id"),
                text(node, "nome", "nomePaciente", "paciente"),
                onlyDigits(text(node, "cpf", "cpfPaciente", "documento")),
                text(node, "email", "emailPaciente"),
                phone(node),
                text(node, "dataNascimento", "dataNasc", "nascimento"),
                parseOffset(text(node, "updatedAt", "atualizadoEm", "ultimaAtualizacao", "ultimaDataHora")),
                toMap(node)
        );
    }

    public ExternalAppointmentDTO toAppointment(JsonNode node, Map<String, JsonNode> procedimentos, Map<String, JsonNode> medicos) {
        JsonNode pacientePayload = object(node, "paciente");
        JsonNode procedimentoPayload = object(
                node,
                "procedimento",
                "procedimentoAgenda",
                "procedimentoAgendado",
                "procedimentoPlanoOperadora"
        );
        JsonNode medicoPayload = object(node, "medico");
        String codProcedimento = firstNonBlank(
                text(node, "codProcedimento", "codprocedimento", "idProcedimento"),
                text(procedimentoPayload, "codProcedimento", "codprocedimento", "idProcedimento")
        );
        String codMedico = firstNonBlank(
                text(node, "codMedico", "codmedico", "idMedico"),
                text(medicoPayload, "codMedico", "codmedico", "idMedico")
        );
        JsonNode procedimento = catalogOrPayload(procedimentos, codProcedimento, procedimentoPayload);
        JsonNode medico = catalogOrPayload(medicos, codMedico, medicoPayload);
        OffsetDateTime startAt = appointmentStart(node);
        Integer duration = integer(node, "duracao", "duracaoMinutos", "intervalo");
        if (duration == null) {
            duration = integer(procedimento, "duracao", "duracaoMinutos");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("medware", toMap(node));
        if (!procedimento.isMissingNode() && !procedimento.isEmpty()) {
            payload.put("procedimento", toMap(procedimento));
        }
        if (!medico.isMissingNode() && !medico.isEmpty()) {
            payload.put("medico", toMap(medico));
        }
        putIfNotBlank(payload, "medicoNome", firstNonBlank(
                text(node, "medicoNome", "nomeMedico"),
                text(medico, "nome", "nomeMedico", "medico")
        ));
        putIfNotBlank(payload, "codMedico", codMedico);
        putIfNotBlank(payload, "codProcedimento", codProcedimento);
        putIfNotBlank(payload, "codPlano", text(node, "codPlano", "codplano"));
        putIfNotBlank(payload, "codEspecialidade", text(node, "codEspecialidade", "codespecialidade"));
        putIfNotBlank(payload, "codUnidade", text(node, "codUnidade", "codunidade"));

        return new ExternalAppointmentDTO(
                text(node, "codAgendamento", "codagendamento", "idAgendamento", "id", "codAgenda"),
                firstNonBlank(
                        text(node, "codPaciente", "codpaciente", "idPaciente"),
                        text(pacientePayload, "codPaciente", "codpaciente", "idPaciente")
                ),
                startAt,
                appointmentEnd(node, startAt, duration),
                appointmentType(node, procedimento),
                serviceName(node, procedimento),
                status(node),
                confirmedAt(node),
                canceledAt(node),
                text(node, "motivoCancelamento", "motivo", "observacaoCancelamento"),
                payload
        );
    }

    public Map<String, JsonNode> indexBy(JsonNode response, String... fields) {
        Map<String, JsonNode> indexed = new LinkedHashMap<>();
        for (JsonNode item : extractItems(response)) {
            String key = text(item, fields);
            if (key != null) {
                indexed.putIfAbsent(key, item);
            }
        }
        return indexed;
    }

    private List<JsonNode> toList(JsonNode array) {
        List<JsonNode> items = new ArrayList<>();
        array.forEach(items::add);
        return items;
    }

    private String phone(JsonNode node) {
        String ddd = onlyDigits(text(node, "numeroCelularddd", "dddCelular", "ddd"));
        String number = onlyDigits(text(node, "numeroCelular", "celular", "telefonePaciente", "telefone"));
        if (number != null && ddd != null && !number.startsWith(ddd)) {
            return ddd + number;
        }
        if (number != null) {
            return number;
        }
        JsonNode telefones = find(node, "telefones");
        if (telefones != null && telefones.isArray()) {
            for (JsonNode telefone : telefones) {
                String value = onlyDigits(text(telefone, "numero", "telefone", "celular"));
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private OffsetDateTime appointmentStart(JsonNode node) {
        OffsetDateTime direct = parseOffset(text(
                node,
                "dataHoraAgenda",
                "dataHoraAgendada",
                "dataHoraReferencia",
                "dataHoraInicio",
                "dataHora",
                "inicio"
        ));
        if (direct != null) {
            return direct;
        }
        String date = text(node, "dataAgendamento", "data", "dataInicio");
        String time = text(node, "horaAgendamento", "hora", "horaInicio");
        if (date != null && time != null) {
            return parseOffset(date + " " + time);
        }
        return parseOffset(date);
    }

    private OffsetDateTime appointmentEnd(JsonNode node, OffsetDateTime startAt, Integer durationMinutes) {
        OffsetDateTime direct = parseOffset(text(node, "dataHoraFim", "fim", "dataHoraFinal"));
        if (direct != null) {
            return direct;
        }
        return startAt != null && durationMinutes != null && durationMinutes > 0
                ? startAt.plusMinutes(durationMinutes)
                : null;
    }

    private String appointmentType(JsonNode node, JsonNode procedimento) {
        String raw = text(node, "tipo", "tipoAtendimento", "tipoProcedimento");
        if (raw != null) {
            String normalized = raw.trim().toUpperCase(Locale.ROOT);
            if (normalized.contains("EXAME") || "2".equals(normalized)) {
                return "EXAME";
            }
            if (normalized.contains("CONSULTA") || "1".equals(normalized)) {
                return "CONSULTA";
            }
        }
        JsonNode consulta = find(procedimento, "consulta");
        if (consulta != null && consulta.isBoolean()) {
            return consulta.asBoolean() ? "CONSULTA" : "EXAME";
        }
        return "EXAME";
    }

    private String serviceName(JsonNode node, JsonNode procedimento) {
        String fromAppointment = text(node, "servicoNome", "procedimento", "descricaoProcedimento", "nomeProcedimento");
        if (fromAppointment != null) {
            return fromAppointment;
        }
        return text(procedimento, "descricaoProcedimento", "procedimento", "nome", "descricao");
    }

    private String status(JsonNode node) {
        String raw = text(node, "status", "statusAgendamento", "descricaoStatus");
        if (raw != null) {
            String normalized = raw.trim().toUpperCase(Locale.ROOT);
            if (normalized.contains("CANCEL")) {
                return "CANCELADO";
            }
            if (normalized.contains("CONFIRM")) {
                return "CONFIRMADO";
            }
            if (normalized.contains("REALIZ") || normalized.contains("ATEND")) {
                return "REALIZADO";
            }
            return normalized.replaceAll("\\s+", "_");
        }
        String code = text(node, "codStatusAgendamento", "codstatusagendamento");
        if ("1".equals(code)) {
            return "AGENDADO";
        }
        if ("2".equals(code)) {
            return "CONFIRMADO";
        }
        if ("3".equals(code)) {
            return "CANCELADO";
        }
        return "AGENDADO";
    }

    private OffsetDateTime confirmedAt(JsonNode node) {
        String status = status(node);
        OffsetDateTime value = parseOffset(text(
                node,
                "confirmadoEm",
                "dataConfirmacao",
                "dataHoraConfirmacao",
                "dataHoraConfirmado"
        ));
        if (value != null) {
            return value;
        }
        return "CONFIRMADO".equals(status) ? appointmentStart(node) : null;
    }

    private OffsetDateTime canceledAt(JsonNode node) {
        OffsetDateTime value = parseOffset(text(node, "canceladoEm", "dataCancelamento", "dataHoraCancelamento"));
        if (value != null) {
            return value;
        }
        return "CANCELADO".equals(status(node)) ? appointmentStart(node) : null;
    }

    private Integer integer(JsonNode node, String... names) {
        String value = text(node, names);
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private OffsetDateTime parseOffset(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        for (DateTimeFormatter formatter : List.of(DateTimeFormatter.ISO_OFFSET_DATE_TIME, DateTimeFormatter.ISO_DATE_TIME)) {
            try {
                return OffsetDateTime.parse(trimmed, formatter);
            } catch (DateTimeParseException ignored) {
                try {
                    LocalDateTime local = LocalDateTime.parse(trimmed, formatter);
                    return local.atZone(MEDWARE_ZONE).toOffsetDateTime();
                } catch (DateTimeParseException ignoredAgain) {
                    // tenta o proximo formato
                }
            }
        }
        try {
            return LocalDateTime.parse(trimmed, BR_DATE_TIME).atZone(MEDWARE_ZONE).toOffsetDateTime();
        } catch (DateTimeParseException ignored) {
            // tenta apenas data
        }
        try {
            return LocalDate.parse(trimmed, BR_DATE).atTime(LocalTime.MIDNIGHT).atZone(MEDWARE_ZONE).toOffsetDateTime();
        } catch (DateTimeParseException ignored) {
            // tenta ISO local date
        }
        try {
            return LocalDate.parse(trimmed).atTime(LocalTime.MIDNIGHT).atZone(MEDWARE_ZONE).toOffsetDateTime();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private Map<String, Object> toMap(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return Map.of();
        }
        Map<String, Object> converted = objectMapper.convertValue(node, new TypeReference<>() {});
        return converted == null ? Map.of() : new LinkedHashMap<>(converted);
    }

    private void putIfNotBlank(Map<String, Object> payload, String key, String value) {
        if (value != null && !value.isBlank()) {
            payload.put(key, value);
        }
    }

    private JsonNode catalogOrPayload(Map<String, JsonNode> catalog, String code, JsonNode payload) {
        JsonNode item = code == null ? null : catalog.get(code);
        if (item != null) {
            return item;
        }
        return payload == null ? objectMapper.createObjectNode() : payload;
    }

    private JsonNode object(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = find(node, name);
            if (value != null && value.isObject()) {
                return value;
            }
        }
        return objectMapper.createObjectNode();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String onlyDigits(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        return digits.isBlank() ? null : digits;
    }

    private String text(JsonNode node, String... names) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        for (String name : names) {
            JsonNode value = find(node, name);
            if (value == null || value.isNull() || value.isMissingNode()) {
                continue;
            }
            String text = value.isTextual() ? value.asText() : value.toString();
            if (!text.isBlank() && !"null".equalsIgnoreCase(text)) {
                return text.replaceAll("^\"|\"$", "");
            }
        }
        return null;
    }

    private JsonNode find(JsonNode node, String name) {
        if (node == null || name == null) {
            return null;
        }
        JsonNode direct = node.get(name);
        if (direct != null) {
            return direct;
        }
        var fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
