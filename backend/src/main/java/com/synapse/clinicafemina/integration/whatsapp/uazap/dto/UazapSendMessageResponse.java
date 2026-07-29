package com.synapse.clinicafemina.integration.whatsapp.uazap.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * DTO da resposta de envio da Uzapi/Autotic.
 * O ID correlacionável do domínio é {@code messages[0].id}; {@code messageId} é interno.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UazapSendMessageResponse(
        Integer statusCode,
        String status,
        String message,
        String queueId,
        String messageId,
        List<UazapContact> contacts,
        List<UazapMessageReference> messages,
        String error
) {

    public UazapSendMessageResponse {
        contacts = contacts == null ? List.of() : List.copyOf(contacts);
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
