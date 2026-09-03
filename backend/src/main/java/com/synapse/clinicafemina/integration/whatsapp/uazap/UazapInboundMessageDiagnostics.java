package com.synapse.clinicafemina.integration.whatsapp.uazap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

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
                    String rawType = message.path("type").asText("");
                    String type = safeValue(rawType.isBlank() ? "ausente" : rawType.toLowerCase(java.util.Locale.ROOT));
                    JsonNode media = mediaNode(message, rawType);
                    String mimeType = media.isObject()
                            ? safeValue(firstText(media, "mime_type", "mimeType", "mimetype"))
                            : "ausente";
                    boolean mediaIdPresent = media.isObject() && hasText(media, "id", "media_id", "mediaId")
                            || hasText(message, "media_id", "mediaId");
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

    private static JsonNode mediaNode(JsonNode message, String type) {
        JsonNode media = type == null || type.isBlank() ? MissingNode.getInstance()
                : message.path(type.toLowerCase(java.util.Locale.ROOT));
        if (!media.isObject()) {
            media = message.path("media");
        }
        return media;
    }

    private static boolean hasText(JsonNode object, String... keys) {
        return !firstText(object, keys).isBlank();
    }

    private static String firstText(JsonNode object, String... keys) {
        for (String key : keys) {
            JsonNode value = object.path(key);
            if (!value.isMissingNode() && !value.isNull() && !value.asText("").isBlank()) {
                return value.asText("");
            }
        }
        return "";
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
