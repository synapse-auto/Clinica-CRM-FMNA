package com.synapse.clinicafemina.integration.whatsapp.uazap;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Descarta eventos sem suporte antes que entrem no pipeline inbound compartilhado. */
@Component
public class UazapInboundEventFilter {

    private static final String STATUS_BROADCAST = "status@broadcast";
    private static final Set<String> ORIGIN_FIELDS = Set.of(
            "chatid",
            "remotejid",
            "key.remotejid",
            "from",
            "sender",
            "participant",
            "jid"
    );

    /**
     * Reconhece Status exclusivamente pela origem real do chat. Os nomes aceitos cobrem tanto o
     * envelope Meta-compatível quanto campos preservados do evento nativo da UAZAP.
     */
    public boolean deveIgnorar(JsonNode payload) {
        if (payload == null) {
            return false;
        }
        for (JsonNode entry : payload.path("entry")) {
            for (JsonNode change : entry.path("changes")) {
                JsonNode value = change.path("value");
                if (origemDiretaStatus(value)) {
                    return true;
                }
                for (JsonNode message : value.path("messages")) {
                    if (origemDiretaStatus(message) || origemDiretaStatus(message.path("key"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean origemDiretaStatus(JsonNode node) {
        if (node == null || !node.isObject()) {
            return false;
        }
        var fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (campoDeOrigem(field.getKey()) && contemValorStatus(field.getValue())) {
                return true;
            }
        }
        return false;
    }

    private boolean campoDeOrigem(String fieldName) {
        String normalized = fieldName == null
                ? ""
                : fieldName.replace("_", "").toLowerCase(Locale.ROOT);
        return ORIGIN_FIELDS.contains(normalized);
    }

    private boolean contemValorStatus(JsonNode value) {
        if (value == null || value.isNull()) {
            return false;
        }
        if (value.isTextual()) {
            return STATUS_BROADCAST.equalsIgnoreCase(value.asText().trim());
        }
        if (value.isContainerNode()) {
            for (JsonNode child : value) {
                if (contemValorStatus(child)) {
                    return true;
                }
            }
        }
        return false;
    }
}
