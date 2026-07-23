package com.synapse.clinicafemina.integration.whatsapp.uazap;

import com.synapse.clinicafemina.integration.WhatsappOutboundClient;
import com.synapse.clinicafemina.integration.whatsapp.WhatsappMediaDownloader;
import com.synapse.clinicafemina.integration.whatsapp.config.WhatsappProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Recuperação de mídia binária via UAZAP.
 *
 * <p><strong>Contrato confirmado no OpenAPI da UAZAP:</strong>
 * {@code GET /{username}/{version}/{mediaId}} (Bearer auth) retorna {@code {"id","url"}} — ou
 * seja, devolve uma URL, não o binário. O <em>segundo hop</em> (buscar os bytes em {@code url})
 * NÃO está documentado: auth, Content-Type e se é CDN direto ou assinado são desconhecidos.</p>
 *
 * <p>Por não inventar esse contrato, este downloader NUNCA baixa o binário e NUNCA chama
 * {@link WhatsappOutboundClient} (o client Meta). Apenas os metadados (ex.: {@code whatsappMediaId})
 * já persistidos pelo chamador permanecem disponíveis; o binário local fica PENDENTE até que o
 * contrato do segundo hop seja confirmado.</p>
 */
@Slf4j
@Component
public class UazapWhatsappMediaDownloader implements WhatsappMediaDownloader {

    private final WhatsappProperties whatsappProperties;

    public UazapWhatsappMediaDownloader(WhatsappProperties whatsappProperties) {
        this.whatsappProperties = whatsappProperties;
    }

    @Override
    public boolean supports(String phoneNumberId) {
        String uazapPhoneNumberId = whatsappProperties.getUazap().getPhoneNumberId();
        return uazapPhoneNumberId != null
                && !uazapPhoneNumberId.isBlank()
                && uazapPhoneNumberId.equals(phoneNumberId);
    }

    @Override
    public WhatsappOutboundClient.MidiaBaixada download(String mediaId) {
        log.warn(
                "Download de binário de mídia UAZAP pendente: contrato de recuperação do arquivo "
                        + "binário (segundo hop da URL) ainda não confirmado. Apenas metadados serão "
                        + "persistidos. mediaId={}",
                maskId(mediaId));
        return null;
    }

    private String maskId(String id) {
        if (id == null || id.isBlank()) {
            return "vazio";
        }
        String trimmed = id.trim();
        if (trimmed.length() <= 4) {
            return "****";
        }
        return "****" + trimmed.substring(trimmed.length() - 4);
    }
}
