package com.synapse.clinicafemina.exception;

public class IdempotencyConflictException extends RuntimeException {

    public static final String CODE = "IDEMPOTENCY_CONFLICT";

    public IdempotencyConflictException(String message) {
        super(message);
    }
}
