package com.synapse.clinicafemina.integration.whatsapp;

/**
 * Exceção base para falhas na camada de providers de WhatsApp.
 * Providers específicos (ex.: UAZAP) estendem esta classe para sinalizar erros
 * de envio/integração de forma agnóstica ao chamador.
 */
public class WhatsappProviderException extends RuntimeException {

    public WhatsappProviderException(String message) {
        super(message);
    }

    public WhatsappProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
