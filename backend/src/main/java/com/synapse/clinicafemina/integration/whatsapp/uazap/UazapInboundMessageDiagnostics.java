package com.synapse.clinicafemina.integration.whatsapp.uazap;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Extrai somente metadados estruturais seguros para diagnosticar o webhook inbound UAZAP. */
final class UazapInboundMessageDiagnostics {

    private static final int MAX_KEYS = 25;
    private static final int MAX_VALUE_LENGTH = 80;

    private UazapInboundMessageDiagnostics() {
    }

    static List<MessageDiagnostic> extract(JsonNode payload) {
        List<MessageDiagnostic> diagnostics = new ArrayList<>();
        if (payload == null) {
            return diagnostics;
        }
        for (JsonNode entry : payload.path("entry")) {
            for (JsonNode change : entry.path("changes")) {
                for (JsonNode message : change.path("value").path("messages")) {
                    if (!message.isObject()) {
                        continue;
                    }
                    String type = safeValue(message.path("type").asText("ausente"));
                    JsonNode media = message.path(type);
                    String mimeType = media.isObject()
                            ? safeValue(media.path("mime_type").asText("ausente"))
                            : "ausente";
                    boolean mediaIdPresent = media.isObject()
                            && !media.path("id").asText("").isBlank();
                    diagnostics.add(new MessageDiagnostic(
                            type,
                            safeKeys(message),
                            mimeType,
                            mediaIdPresent));
                }
            }
        }
        return diagnostics;
    }

    private static List<String> safeKeys(JsonNode message) {
        List<String> keys = new ArrayList<>();
        var fields = message.fields();
        while (fields.hasNext() && keys.size() < MAX_KEYS) {
            Map.Entry<String, JsonNode> field = fields.next();
            keys.add(safeValue(field.getKey()));
        }
        keys.sort(Comparator.naturalOrder());
        return List.copyOf(keys);
    }

    private static String safeValue(String value) {
        if (value == null || value.isBlank()) {
            return "ausente";
        }
        String sanitized = value.replaceAll("[^A-Za-z0-9._/+\\-]", "_");
        return sanitized.substring(0, Math.min(sanitized.length(), MAX_VALUE_LENGTH));
    }

    record MessageDiagnostic(
            String messageType,
            List<String> messageKeys,
            String mimeType,
            boolean mediaIdPresent) {
    }
}
