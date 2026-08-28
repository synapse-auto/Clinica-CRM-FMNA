package com.synapse.clinicafemina.integration.whatsapp.uazap;

import com.synapse.clinicafemina.integration.whatsapp.WhatsappProviderType;
import com.synapse.clinicafemina.integration.whatsapp.config.WhatsappProperties;
import com.synapse.clinicafemina.integration.whatsapp.model.WhatsappMessageType;
import com.synapse.clinicafemina.integration.whatsapp.model.WhatsappSendResult;
import com.synapse.clinicafemina.integration.whatsapp.uazap.dto.UazapContact;
import com.synapse.clinicafemina.integration.whatsapp.uazap.dto.UazapMessageReference;
import com.synapse.clinicafemina.integration.whatsapp.uazap.dto.UazapSendMessageResponse;
import com.synapse.clinicafemina.integration.whatsapp.uazap.exception.UazapException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Cliente HTTP isolado para envio outbound via Uzapi/Autotic.
 *
 * <p>Autenticação: {@code Authorization: Bearer <token>}. Endpoint:
 * {@code POST {baseUrl}/{username}/{version}/{phoneNumberId}/messages}. O identificador externo
 * persistido é {@code messages[0].id}; {@code queueId} e {@code messageId} são internos.</p>
 *
 * <p><strong>Sem retry automático</strong> (não há chave de idempotência de envio comprovada).
 * Logs sanitizados: nunca registram token, {@code Authorization}, corpo integral, telefone
 * completo ou URL com segredo.</p>
 */
@Slf4j
@Component
public class UazapClient {

    private final RestClient restClient;
    private final WhatsappProperties properties;
    private final WhatsappProperties.Uazap config;

    @Autowired
    public UazapClient(RestClient.Builder builder, WhatsappProperties properties) {
        this(buildRestClient(builder, properties.getUazap()), properties);
    }

    /** Construtor visível para testes: injeta um {@link RestClient} já vinculado (ex.: MockRestServiceServer). */
    UazapClient(RestClient restClient, WhatsappProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
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
        payload.put("delayMessage", 0);
        payload.put("delayTyping", 0);
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
        // Referências retornadas pelo upload são media IDs; URLs públicas continuam aceitas.
        media.put(mediaReference.startsWith("http://") || mediaReference.startsWith("https://") ? "link" : "id", mediaReference);
        if (caption != null && !caption.isBlank()) {
            media.put("caption", caption);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("to", onlyDigits(toE164));
        payload.put("type", mediaType);
        payload.put(mediaType, media);
        return post(payload);
    }

    public String uploadMedia(org.springframework.core.io.Resource recurso, String contentType, String nomeArquivo) {
        validarConfiguracao();
        try {
            LinkedMultiValueMap<String, Object> multipart = new LinkedMultiValueMap<>();
            multipart.add("file", recurso);
            multipart.add("messaging_product", "whatsapp");

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri(mediaUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.getToken())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(multipart)
                    .retrieve()
                    .body(Map.class);
            if (response == null || !(response.get("id") instanceof String id) || id.isBlank()) {
                throw new UazapException("Resposta da Uzapi sem identificador de mídia");
            }
            log.info("Upload de mídia Uzapi aceito. mediaId={}", maskId(id));
            return id.trim();
        } catch (RestClientResponseException exception) {
            log.error("Upload de mídia Uzapi rejeitado pelo servidor. status={}", exception.getStatusCode().value());
            throw new UazapException("Uzapi retornou status HTTP " + exception.getStatusCode().value(), exception);
        } catch (ResourceAccessException exception) {
            log.error("Falha de I/O no upload de mídia Uzapi. tipoErro={}", exception.getClass().getSimpleName());
            throw new UazapException("Falha de conexão ou timeout ao contatar a Uzapi", exception);
        } catch (RestClientException exception) {
            log.error("Resposta inválida no upload de mídia Uzapi. tipoErro={}", exception.getClass().getSimpleName());
            throw new UazapException("Resposta inválida da Uzapi", exception);
        }
    }

    private WhatsappSendResult post(Map<String, Object> payload) {
        validarConfiguracao();
        String url = messagesUrl();
        try {
            UazapSendMessageResponse response = restClient.post()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.getToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(UazapSendMessageResponse.class);

            return interpretarResposta(response);

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

    private String mediaUrl() {
        String base = config.getBaseUrl() == null ? "" : config.getBaseUrl().replaceAll("/+$", "");
        return base + "/" + config.getUsername() + "/" + config.getVersion()
                + "/" + config.getPhoneNumberId() + "/media";
    }

    private static String onlyDigits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private WhatsappSendResult interpretarResposta(UazapSendMessageResponse response) {
        if (response == null) {
            throw new UazapException("Resposta da Uzapi ausente");
        }
        if (!"success".equals(response.status()) || preenchido(response.error())) {
            log.warn("Envio Uzapi rejeitado no corpo. status={} possuiErro={}",
                    response.status(), preenchido(response.error()));
            throw new UazapException("Uzapi rejeitou o envio da mensagem");
        }

        String queueId = exigirIdentificador(response.queueId(), "queueId");
        String messageId = exigirIdentificador(response.messageId(), "messageId");
        String wamid = response.messages().stream()
                .map(UazapMessageReference::id)
                .filter(this::preenchido)
                .map(String::trim)
                .findFirst()
                .orElseThrow(() -> new UazapException("Resposta da Uzapi sem identificador WhatsApp"));
        String confirmedRecipient = response.contacts().stream()
                .map(UazapContact::waId)
                .filter(this::preenchido)
                .map(String::trim)
                .findFirst()
                .orElse(null);

        log.info("Envio Uzapi aceito. endpoint=messages status={} queueId={} messageId={} wamid={}",
                response.status(), maskId(queueId), maskId(messageId), maskId(wamid));
        return new WhatsappSendResult(wamid, WhatsappProviderType.UAZAP, confirmedRecipient);
    }

    private void validarConfiguracao() {
        if (!properties.isEnabled()) {
            throw new UazapException("Configuracao Uzapi invalida: WHATSAPP_ENABLED deve estar habilitado");
        }
        if (properties.resolveProvider() != WhatsappProviderType.UAZAP) {
            throw new UazapException("Configuracao Uzapi invalida: WHATSAPP_PROVIDER deve ser UAZAP");
        }
        List<String> ausentes = new ArrayList<>();
        if (!preenchido(config.getBaseUrl())) ausentes.add("UAZAP_BASE_URL");
        if (!preenchido(config.getUsername())) ausentes.add("UAZAP_USERNAME");
        if (!preenchido(config.getVersion())) ausentes.add("UAZAP_VERSION");
        if (!preenchido(config.getPhoneNumberId())) ausentes.add("UAZAP_PHONE_NUMBER_ID");
        if (!preenchido(config.getToken())) ausentes.add("UAZAP_TOKEN");
        if (!ausentes.isEmpty()) {
            throw new UazapException("Configuracao Uzapi incompleta: " + String.join(", ", ausentes));
        }
    }

    private boolean preenchido(String value) {
        return value != null && !value.isBlank();
    }

    private String exigirIdentificador(String value, String field) {
        if (!preenchido(value)) {
            throw new UazapException("Resposta da Uzapi sem " + field);
        }
        return value.trim();
    }

    private String maskId(String value) {
        if (!preenchido(value) || value.length() <= 4) {
            return "****";
        }
        return "****" + value.substring(value.length() - 4);
    }
}
