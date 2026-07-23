package com.synapse.clinicafemina.integration.whatsapp.meta;

import com.synapse.clinicafemina.integration.WhatsappOutboundClient;
import com.synapse.clinicafemina.integration.whatsapp.WhatsappMediaDownloader;
import com.synapse.clinicafemina.integration.whatsapp.config.WhatsappProperties;
import org.springframework.stereotype.Component;

/**
 * Recuperação de mídia binária via Meta — encapsula {@link WhatsappOutboundClient#baixarMidia}
 * sem alterar seu comportamento.
 *
 * <p>Atua como downloader PADRÃO: atende qualquer {@code phone_number_id} que não seja
 * explicitamente o da UAZAP configurada ({@code app.whatsapp.uazap.phone-number-id}). Isso
 * preserva o comportamento atual para a UltraMedical mesmo quando o phone_number_id do payload
 * não é conhecido antecipadamente pelo mapeador.</p>
 */
@Component
public class MetaWhatsappMediaDownloader implements WhatsappMediaDownloader {

    private final WhatsappOutboundClient metaClient;
    private final WhatsappProperties whatsappProperties;

    public MetaWhatsappMediaDownloader(WhatsappOutboundClient metaClient, WhatsappProperties whatsappProperties) {
        this.metaClient = metaClient;
        this.whatsappProperties = whatsappProperties;
    }

    @Override
    public boolean supports(String phoneNumberId) {
        String uazapPhoneNumberId = whatsappProperties.getUazap().getPhoneNumberId();
        return uazapPhoneNumberId == null
                || uazapPhoneNumberId.isBlank()
                || !uazapPhoneNumberId.equals(phoneNumberId);
    }

    @Override
    public WhatsappOutboundClient.MidiaBaixada download(String mediaId) {
        return metaClient.baixarMidia(mediaId);
    }
}
