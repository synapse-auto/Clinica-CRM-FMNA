package com.synapse.clinicafemina.integration.whatsapp.uazap.exception;

import com.synapse.clinicafemina.integration.whatsapp.WhatsappProviderException;

/**
 * Falha na integração com a UAZAP (envio ou resposta inválida).
 * Mensagens nunca contêm token, corpo integral, telefone completo ou URL com segredo.
 */
public class UazapException extends WhatsappProviderException {

    public UazapException(String message) {
        super(message);
    }

    public UazapException(String message, Throwable cause) {
        super(message, cause);
    }
}
