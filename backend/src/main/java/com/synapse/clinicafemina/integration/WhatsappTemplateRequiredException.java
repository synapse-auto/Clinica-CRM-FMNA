package com.synapse.clinicafemina.integration;

public class WhatsappTemplateRequiredException extends RuntimeException {

    public static final String MESSAGE =
            "A Meta exige template aprovado para iniciar conversa ativa ou responder fora da janela de 24h.";

    public WhatsappTemplateRequiredException() {
        super(MESSAGE);
    }
}
