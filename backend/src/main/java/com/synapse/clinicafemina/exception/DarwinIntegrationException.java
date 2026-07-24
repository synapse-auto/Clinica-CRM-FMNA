package com.synapse.clinicafemina.exception;

/**
 * Erro sanitizado ao comunicar com a API externa Darwin. Nunca carrega token, CPF,
 * dados de paciente ou corpo de resposta — apenas o status HTTP de origem e uma
 * mensagem genérica adequada para exposição ao cliente.
 */
public class DarwinIntegrationException extends RuntimeException {

    public static final String CODE = "DARWIN_INTEGRATION_ERROR";

    private final int upstreamStatus;

    public DarwinIntegrationException(int upstreamStatus, String message) {
        super(message);
        this.upstreamStatus = upstreamStatus;
    }

    public int upstreamStatus() {
        return upstreamStatus;
    }
}
