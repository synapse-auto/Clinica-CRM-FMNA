package com.synapse.clinicafemina.integration.whatsapp.uazap;

/**
 * Resposta crua (não interpretada) do endpoint {@code getPicture} da UAZAP.
 * Carrega o status HTTP mesmo em caso de erro (4xx/5xx) — quem decide o que fazer com isso é o
 * parser/serviço, nunca o cliente HTTP.
 */
public record UazapPictureRawResponse(int statusCode, String contentType, byte[] body) {
}
