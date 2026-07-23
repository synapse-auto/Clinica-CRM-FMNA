package com.synapse.clinicafemina.integration.whatsapp.model;

/**
 * Tipos de conteúdo de mensagem suportados de forma agnóstica ao provider.
 * Mapeado para o vocabulário de cada provider (Meta/UAZAP) nas camadas de adaptação.
 */
public enum WhatsappMessageType {
    TEXT,
    IMAGE,
    AUDIO,
    VIDEO,
    DOCUMENT
}
