package com.synapse.clinicafemina.integration.whatsapp;

import com.synapse.clinicafemina.integration.WhatsappOutboundClient;

/**
 * Abstração de recuperação de mídia binária inbound, independente do provider de origem.
 *
 * <p>Resolução por {@code metadata.phone_number_id} do payload webhook (Meta ou UAZAP) — nunca
 * por {@code if/else} espalhado no chamador. Implementações: {@code MetaWhatsappMediaDownloader}
 * (comportamento Meta existente, inalterado) e {@code UazapWhatsappMediaDownloader}.</p>
 */
public interface WhatsappMediaDownloader {

    /** Indica se este downloader atende o {@code phone_number_id} do payload inbound. */
    boolean supports(String phoneNumberId);

    /**
     * Recupera o binário da mídia. Pode retornar {@code null} quando o download não é
     * possível ou seguro (ex.: contrato de recuperação de binário não confirmado) — o chamador
     * trata isso como "mídia pendente": persiste os metadados disponíveis sem falhar.
     */
    WhatsappOutboundClient.MidiaBaixada download(String mediaId);
}
