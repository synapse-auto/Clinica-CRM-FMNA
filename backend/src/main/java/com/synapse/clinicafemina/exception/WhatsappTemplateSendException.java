package com.synapse.clinicafemina.exception;

public class WhatsappTemplateSendException extends RuntimeException {

    public WhatsappTemplateSendException(Throwable cause) {
        super("Nao foi possivel enviar o template do WhatsApp.", cause);
    }
}
