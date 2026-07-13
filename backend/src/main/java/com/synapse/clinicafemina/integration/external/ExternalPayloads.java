package com.synapse.clinicafemina.integration.external;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

final class ExternalPayloads {

    private ExternalPayloads() {
    }

    static Map<String, Object> immutableNullSafeCopy(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }
}
