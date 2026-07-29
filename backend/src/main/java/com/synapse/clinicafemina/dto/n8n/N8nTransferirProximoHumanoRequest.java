package com.synapse.clinicafemina.dto.n8n;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.synapse.clinicafemina.dto.TransferirAtendimentoRequest;
import com.synapse.clinicafemina.exception.N8nTransferPayloadException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Contrato do rodízio N8N: a ordem da lista define a ordem de atendimento. */
public record N8nTransferirProximoHumanoRequest(
        @JsonAlias({"atendentes_ids", "atendenteIds", "idsAtendentes"}) JsonNode atendentesIds,
        String motivo,
        String motivoTransferencia,
        String resumoTransferencia
) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_ATENDENTES = 20;
    private static final int MOTIVO_MAX_LENGTH = 500;
    private static final int RESUMO_MAX_LENGTH = 4096;
    private static final String IDS_ERROR =
            "Informe uma lista de IDs numéricos positivos de gestor ou recepcionista.";

    public N8nTransferirProximoHumanoRequest(
            List<Long> atendentesIds,
            String motivo,
            String motivoTransferencia,
            String resumoTransferencia
    ) {
        this(toJsonArray(atendentesIds), motivo, motivoTransferencia, resumoTransferencia);
    }

    public TransferirAtendimentoRequest paraTransferencia(Long novoAtendenteId) {
        validar();
        return new TransferirAtendimentoRequest(
                novoAtendenteId,
                null,
                normalizarTexto(resumoTransferencia),
                normalizarTexto(motivoEfetivo())
        );
    }

    public List<Long> idsOrdenados() {
        Map<String, String> errors = new LinkedHashMap<>();
        List<Long> ids = normalizarAtendentesIds(errors);
        validarIds(ids, errors);
        if (!errors.isEmpty()) {
            throw new N8nTransferPayloadException(errors);
        }
        return List.copyOf(ids);
    }

    public String validarIdempotencyKey(String idempotencyKey) {
        Map<String, String> errors = new LinkedHashMap<>();
        String normalized = idempotencyKey == null ? null : idempotencyKey.trim();
        if (normalized == null || normalized.isEmpty()) {
            errors.put("idempotencyKey", "Informe o header Idempotency-Key.");
        } else if (normalized.length() > 120) {
            errors.put("idempotencyKey", "Informe no máximo 120 caracteres.");
        }
        if (!errors.isEmpty()) {
            throw new N8nTransferPayloadException(errors);
        }
        return normalized;
    }

    public String tipoAtendentesIds() {
        if (atendentesIds == null || atendentesIds.isNull()) {
            return "ausente";
        }
        return atendentesIds.getNodeType().name().toLowerCase();
    }

    public int quantidadeAtendentes() {
        try {
            return idsOrdenados().size();
        } catch (N8nTransferPayloadException ignored) {
            return 0;
        }
    }

    public String fingerprint(Long atendimentoId) {
        List<Long> ids = idsOrdenados();
        String canonical = part(atendimentoId)
                + part(ids.stream().map(String::valueOf).toList())
                + part(normalizarTexto(motivoEfetivo()))
                + part(normalizarTexto(resumoTransferencia));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponível", ex);
        }
    }

    private void validar() {
        Map<String, String> errors = new LinkedHashMap<>();
        List<Long> ids = normalizarAtendentesIds(errors);
        validarIds(ids, errors);
        validarTexto("motivoTransferencia", motivoEfetivo(), MOTIVO_MAX_LENGTH, errors);
        validarTexto("resumoTransferencia", resumoTransferencia, RESUMO_MAX_LENGTH, errors);
        if (!errors.isEmpty()) {
            throw new N8nTransferPayloadException(errors);
        }
    }

    private List<Long> normalizarAtendentesIds(Map<String, String> errors) {
        JsonNode idsNode = atendentesIds;
        if (idsNode != null && idsNode.isTextual()) {
            String serialized = idsNode.textValue() == null ? "" : idsNode.textValue().trim();
            try {
                idsNode = OBJECT_MAPPER.readTree(serialized);
            } catch (Exception ignored) {
                errors.put("atendentesIds", IDS_ERROR);
                return List.of();
            }
        }
        if (idsNode == null || !idsNode.isArray()) {
            errors.put("atendentesIds", IDS_ERROR);
            return List.of();
        }

        List<Long> ids = new ArrayList<>();
        for (JsonNode item : idsNode) {
            Long id = normalizarId(item);
            if (id == null) {
                errors.put("atendentesIds", IDS_ERROR);
                return List.of();
            }
            ids.add(id);
        }
        return ids;
    }

    private Long normalizarId(JsonNode item) {
        if (item != null && item.isIntegralNumber() && item.canConvertToLong()) {
            long value = item.longValue();
            return value > 0 ? value : null;
        }
        if (item != null && item.isTextual()) {
            String value = item.textValue() == null ? "" : item.textValue().trim();
            if (value.matches("[1-9]\\d*")) {
                try {
                    return Long.parseLong(value);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private void validarIds(List<Long> ids, Map<String, String> errors) {
        if (errors.containsKey("atendentesIds")) {
            return;
        }
        if (ids.isEmpty()) {
            errors.put("atendentesIds", "Informe ao menos um atendente para o rodízio.");
        } else if (ids.size() > MAX_ATENDENTES) {
            errors.put("atendentesIds", "Informe no máximo " + MAX_ATENDENTES + " atendentes.");
        } else if (new LinkedHashSet<>(ids).size() != ids.size()) {
            errors.put("atendentesIds", "Não repita atendentes no rodízio.");
        }
    }

    private void validarTexto(String field, String value, int maxLength, Map<String, String> errors) {
        if (value != null && value.length() > maxLength) {
            errors.put(field, "Informe no máximo " + maxLength + " caracteres.");
        }
    }

    private String normalizarTexto(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replaceAll("<[^>]*>", "").trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String motivoEfetivo() {
        return motivoTransferencia != null ? motivoTransferencia : motivo;
    }

    private String part(Object value) {
        String text = value == null ? "" : value.toString();
        return text.length() + ":" + text;
    }

    private static JsonNode toJsonArray(List<Long> ids) {
        if (ids == null) {
            return null;
        }
        ArrayNode array = JsonNodeFactory.instance.arrayNode();
        ids.forEach(id -> {
            if (id == null) {
                array.addNull();
            } else {
                array.add(id);
            }
        });
        return array;
    }
}
