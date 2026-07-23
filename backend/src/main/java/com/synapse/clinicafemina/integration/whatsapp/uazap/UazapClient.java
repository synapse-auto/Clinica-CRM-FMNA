package com.synapse.clinicafemina.integration.whatsapp.uazap;

import com.synapse.clinicafemina.integration.whatsapp.WhatsappProviderType;
import com.synapse.clinicafemina.integration.whatsapp.config.WhatsappProperties;
import com.synapse.clinicafemina.integration.whatsapp.model.WhatsappMessageType;
import com.synapse.clinicafemina.integration.whatsapp.model.WhatsappSendResult;
import com.synapse.clinicafemina.integration.whatsapp.uazap.dto.UazapSendMessageResponse;
import com.synapse.clinicafemina.integration.whatsapp.uazap.exception.UazapException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Cliente HTTP isolado para envio outbound via UAZAP (contrato confirmado por OpenAPI).
 *
 * <p>Autenticação: {@code Authorization: Bearer <token>}. Endpoint:
 * {@code POST {baseUrl}/{username}/{version}/{phoneNumberId}/messages}. O {@code messageId} da
 * resposta vira o {@code externalMessageId} do domínio.</p>
 *
 * <p><strong>Sem retry automático</strong> (não há chave de idempotência de envio comprovada).
 * Logs sanitizados: nunca registram token, {@code Authorization}, corpo integral, telefone
 * completo ou URL com segredo.</p>
 */
@Slf4j
@Component
public class UazapClient {

    private final RestClient restClient;
    private final WhatsappProperties.Uazap config;

    @Autowired
    public UazapClient(RestClient.Builder builder, WhatsappProperties properties) {
        this(buildRestClient(builder, properties.getUazap()), properties);
    }

    /** Construtor visível para testes: injeta um {@link RestClient} já vinculado (ex.: MockRestServiceServer). */
    UazapClient(RestClient restClient, WhatsappProperties properties) {
        this.restClient = restClient;
        this.config = properties.getUazap();
    }

    private static RestClient buildRestClient(RestClient.Builder builder, WhatsappProperties.Uazap uazap) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(uazap.getConnectTimeoutMs());
        factory.setReadTimeout(uazap.getReadTimeoutMs());
        return builder.clone().requestFactory(factory).build();
    }

    public WhatsappSendResult sendText(String toE164, String body) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("to", onlyDigits(toE164));
        payload.put("type", "text");
        payload.put("text", Map.of("body", body));
        return post(payload);
    }

    public WhatsappSendResult sendMedia(String toE164, WhatsappMessageType type, String mediaReference, String caption) {
        if (type == WhatsappMessageType.TEXT) {
            throw new IllegalArgumentException("sendMedia não aceita o tipo TEXT; use sendText");
        }
        String mediaType = type.name().toLowerCase(Locale.ROOT); // image|audio|video|document
        Map<String, Object> media = new LinkedHashMap<>();
        media.put("link", mediaReference);
        if (caption != null && !caption.isBlank()) {
            media.put("caption", caption);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("to", onlyDigits(toE164));
        payload.put("type", mediaType);
        payload.put(mediaType, media);
        return post(payload);
    }

    private WhatsappSendResult post(Map<String, Object> payload) {
        String url = messagesUrl();
        try {
            UazapSendMessageResponse response = restClient.post()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.getToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(UazapSendMessageResponse.class);

            if (response == null || response.messageId() == null || response.messageId().isBlank()) {
                throw new UazapException("Resposta da UAZAP sem messageId");
            }
            return new WhatsappSendResult(response.messageId(), WhatsappProviderType.UAZAP);

        } catch (RestClientResponseException exception) {
            // Cobre 400/401/403/404/409/429/5xx — status explícito, sem vazar corpo/segredos.
            int status = exception.getStatusCode().value();
            log.error("Envio UAZAP rejeitado pelo servidor. status={}", status);
            throw new UazapException("UAZAP retornou status HTTP " + status, exception);
        } catch (ResourceAccessException exception) {
            // Timeout / falha de conexão.
            log.error("Falha de I/O no envio UAZAP. tipoErro={}", exception.getClass().getSimpleName());
            throw new UazapException("Falha de conexão ou timeout ao contatar a UAZAP", exception);
        } catch (RestClientException exception) {
            // Resposta ilegível / desserialização inválida.
            log.error("Resposta inválida da UAZAP. tipoErro={}", exception.getClass().getSimpleName());
            throw new UazapException("Resposta inválida da UAZAP", exception);
        }
    }

    private String messagesUrl() {
        String base = config.getBaseUrl() == null ? "" : config.getBaseUrl().replaceAll("/+$", "");
        return base + "/" + config.getUsername() + "/" + config.getVersion()
                + "/" + config.getPhoneNumberId() + "/messages";
    }

    private static String onlyDigits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }
}
