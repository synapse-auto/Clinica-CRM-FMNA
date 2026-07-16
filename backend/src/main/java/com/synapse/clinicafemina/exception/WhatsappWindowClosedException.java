package com.synapse.clinicafemina.exception;

public class WhatsappWindowClosedException extends RuntimeException {

    public static final String CODE = "WHATSAPP_TEMPLATE_REQUIRED";
    public static final String MESSAGE =
            "A janela de atendimento do WhatsApp foi encerrada. Envie um template aprovado.";

    public WhatsappWindowClosedException() {
        super(MESSAGE);
    }
}
