package com.synapse.clinicafemina.exception;

public class WhatsappTemplateParametersException extends BadRequestException {

    public static final String CODE = "WHATSAPP_TEMPLATE_PARAMETERS_INVALID";

    public WhatsappTemplateParametersException(String message) {
        super(message);
    }
}
