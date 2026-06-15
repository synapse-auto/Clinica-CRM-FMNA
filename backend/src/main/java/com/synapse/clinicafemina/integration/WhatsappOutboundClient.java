package com.synapse.clinicafemina.integration;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Cliente HTTP para envio de mensagens outbound via Meta WhatsApp Cloud API.
 *
 * Estratégia de retry (conforme contrato {@code whatsapp.md}):
 * <ul>
 *   <li>Retries: 5, backoff exponencial, base 1s, máx 16s</li>
 *   <li>Circuit breaker: 50% falha → abre 60s</li>
 *   <li>Em falha final: motivo persistido + publicado na DLX pelo {@link MensagemService}</li>
 * </ul>
 */
@Slf4j
@Component
public class WhatsappOutboundClient {

    @Value("${app.whatsapp.access-token}")
    private String accessToken;

    @Value("${app.whatsapp.phone-number-id}")
    private String phoneNumberId;

    @Value("${app.whatsapp.graph-api-url}")
    private String graphApiUrl;

    private final RestClient restClient;

    public WhatsappOutboundClient() {
        this.restClient = RestClient.builder().build();
    }

    /**
     * Envia uma mensagem de texto simples e retorna o {@code wamid} (ID da Meta).
     */
    @Retry(name = "whatsapp-send")
    @CircuitBreaker(name = "whatsapp-send", fallbackMethod = "enviarTextoFallback")
    public String enviarTexto(String telefoneE164, String corpo) {
        String url = graphApiUrl + "/" + phoneNumberId + "/messages";

        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "to", telefoneE164,
                "type", "text",
                "text", Map.of("preview_url", false, "body", corpo)
        );

        log.debug("Enviando mensagem WhatsApp via Meta Cloud API");

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        if (response == null || !response.containsKey("messages")) {
            throw new IllegalStateException("Resposta inesperada da Meta API");
        }

        @SuppressWarnings("unchecked")
        java.util.List<Map<String, String>> messages =
                (java.util.List<Map<String, String>>) response.get("messages");

        return messages.getFirst().get("id");
    }

    /**
     * Faz upload de um arquivo de mídia para os servidores da Meta.
     * Retorna o {@code media_id} para uso posterior no envio.
     */
    @Retry(name = "whatsapp-send")
    @CircuitBreaker(name = "whatsapp-send", fallbackMethod = "uploadMidiaFallback")
    public String uploadMidia(org.springframework.core.io.Resource recurso, String contentType, String nomeArquivo) {
        String url = graphApiUrl + "/" + phoneNumberId + "/media";

        // Constrói o body como multipart/form-data manualmente via byte array
        String boundary = "----WhatsappUpload" + System.currentTimeMillis();

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        headers.set(HttpHeaders.CONTENT_TYPE, "multipart/form-data; boundary=" + boundary);

        org.springframework.util.MultiValueMap<String, Object> parts =
                new org.springframework.util.LinkedMultiValueMap<>();
        parts.add("file", recurso);
        parts.add("type", contentType);
        parts.add("messaging_product", "whatsapp");

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .retrieve()
                .body(Map.class);

        if (response == null || !response.containsKey("id")) {
            throw new IllegalStateException("Resposta inesperada no upload de midia");
        }
        return (String) response.get("id");
    }

    @SuppressWarnings("unused")
    public String uploadMidiaFallback(org.springframework.core.io.Resource recurso, String contentType, String nomeArquivo, Throwable t) {
        log.error("Circuit breaker: upload de midia falhou. tipoErro={}", t.getClass().getSimpleName());
        throw new RuntimeException("Upload de midia indisponivel", t);
    }

    /**
     * Envia uma mensagem de mídia referenciando um {@code media_id} já carregado.
     *
     * @param tipo um de: image, audio, video, document
     */
    @Retry(name = "whatsapp-send")
    @CircuitBreaker(name = "whatsapp-send", fallbackMethod = "enviarMidiaFallback")
    public String enviarMidia(String telefoneE164, String tipo, String mediaId) {
        String url = graphApiUrl + "/" + phoneNumberId + "/messages";

        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "to", telefoneE164,
                "type", tipo,
                tipo, Map.of("id", mediaId)
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        if (response == null || !response.containsKey("messages")) {
            throw new IllegalStateException("Resposta inesperada no envio de midia");
        }
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, String>> messages =
                (java.util.List<Map<String, String>>) response.get("messages");
        return messages.getFirst().get("id");
    }

    @SuppressWarnings("unused")
    public String enviarMidiaFallback(String telefoneE164, String tipo, String mediaId, Throwable t) {
        log.error("Circuit breaker: envio de midia falhou. tipoErro={}", t.getClass().getSimpleName());
        throw new RuntimeException("Envio de midia indisponivel", t);
    }

    /**
     * Fallback do circuit breaker — lança exceção para que o MensagemService
     * registre a falha e publique na DLX.
     */
    @SuppressWarnings("unused")
    public String enviarTextoFallback(String telefoneE164, String corpo, Throwable t) {
        log.error("Circuit breaker ativado para envio WhatsApp. tipoErro={}", t.getClass().getSimpleName());
        throw new RuntimeException("WhatsApp indisponivel (circuit breaker aberto)", t);
    }
}
