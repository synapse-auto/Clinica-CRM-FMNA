package com.synapse.clinicafemina.integration.whatsapp.uazap.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO da resposta de envio da UAZAP (contrato confirmado via OpenAPI).
 * Isolado dos modelos internos; {@code messageId} é mapeado para o {@code externalMessageId} do domínio.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UazapSendMessageResponse(
        Integer statusCode,
        String message,
        String queueId,
        String messageId
) {
}
