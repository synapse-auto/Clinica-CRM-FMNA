package com.synapse.clinicafemina.integration.whatsapp.uazap;

/**
 * Publicado por {@code WhatsappInboundMapper} após uma mensagem UAZAP ser persistida (paciente e
 * atendimento já commitados). Consumido apenas após o commit da transação
 * ({@code TransactionPhase.AFTER_COMMIT}), fora do caminho crítico do webhook.
 */
public record UazapPictureEnrichmentRequestedEvent(Long pacienteId) {
}
