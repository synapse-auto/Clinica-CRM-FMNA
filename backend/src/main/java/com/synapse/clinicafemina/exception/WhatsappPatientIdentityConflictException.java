package com.synapse.clinicafemina.exception;

/**
 * Indica que o telefone corresponde a mais de um paciente legítimo e não pode ser
 * escolhido automaticamente sem risco de anexar o atendimento ao cadastro errado.
 */
public class WhatsappPatientIdentityConflictException extends IllegalStateException {

    public static final String CODE = "WHATSAPP_PATIENT_IDENTITY_CONFLICT";

    public WhatsappPatientIdentityConflictException(String message) {
        super(message);
    }
}
