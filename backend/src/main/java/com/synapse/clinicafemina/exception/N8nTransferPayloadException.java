package com.synapse.clinicafemina.exception;

import java.util.Map;

public class N8nTransferPayloadException extends RuntimeException {

    private final Map<String, String> details;

    public N8nTransferPayloadException(Map<String, String> details) {
        super("Payload de transferência inválido.");
        this.details = Map.copyOf(details);
    }

    public Map<String, String> getDetails() {
        return details;
    }
}
