package com.synapse.clinicafemina.exception;

public class DarwinNotAvailableException extends RuntimeException {

    public static final String CODE = "DARWIN_INDISPONIVEL";

    public DarwinNotAvailableException(String message) {
        super(message);
    }
}
