package com.synapse.clinicafemina.integration.whatsapp.meta;

import com.synapse.clinicafemina.integration.WhatsappOutboundClient;
import com.synapse.clinicafemina.integration.whatsapp.WhatsappProvider;
import com.synapse.clinicafemina.integration.whatsapp.WhatsappProviderType;
import com.synapse.clinicafemina.integration.whatsapp.model.WhatsappMessageType;
import com.synapse.clinicafemina.integration.whatsapp.model.WhatsappSendResult;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Adaptador do provider Meta para a abstração {@link WhatsappProvider}.
 *
 * <p><strong>Apenas encapsula</strong> o {@link WhatsappOutboundClient} já existente — não
 * reescreve nem altera seu comportamento (retry, circuit breaker, wamid, janela de 24h,
 * templates e integração RabbitMQ permanecem inalterados). Os métodos originais do client
 * continuam disponíveis e em uso pelos serviços atuais.</p>
 */
@Component
public class MetaWhatsappProvider implements WhatsappProvider {

    private final WhatsappOutboundClient metaClient;

    public MetaWhatsappProvider(WhatsappOutboundClient metaClient) {
        this.metaClient = metaClient;
    }

    @Override
    public WhatsappProviderType getType() {
        return WhatsappProviderType.META;
    }

    @Override
    public WhatsappSendResult sendText(String toE164, String body) {
        // Mesma ordem de chamadas que o consumer fazia antes da centralização: validar, depois enviar.
        metaClient.validarConfiguracao();
        String wamid = metaClient.enviarTexto(toE164, body);
        return new WhatsappSendResult(wamid, WhatsappProviderType.META);
    }

    @Override
    public WhatsappSendResult sendMedia(String toE164, WhatsappMessageType type, String mediaReference, String caption) {
        if (type == WhatsappMessageType.TEXT) {
            throw new IllegalArgumentException("sendMedia não aceita o tipo TEXT; use sendText");
        }
        // O client Meta atual envia mídia por media_id e não recebe legenda — comportamento preservado.
        String wamid = metaClient.enviarMidia(toE164, type.name().toLowerCase(Locale.ROOT), mediaReference);
        return new WhatsappSendResult(wamid, WhatsappProviderType.META);
    }
}
