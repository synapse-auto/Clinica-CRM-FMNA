package com.synapse.clinicafemina.exception;

public class IdempotencyConflictException extends RuntimeException {

    public static final String CODE = "IDEMPOTENCY_KEY_PAYLOAD_CONFLITANTE";

    public IdempotencyConflictException(String message) {
        super(message);
    }
}
