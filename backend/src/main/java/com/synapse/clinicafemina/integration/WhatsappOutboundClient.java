package com.synapse.clinicafemina.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
 
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.web.util.UriComponentsBuilder;
 
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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
 
    @Value("${app.whatsapp.enabled:false}")
    private boolean enabled;
 
    @Value("${app.whatsapp.access-token}")
    private String accessToken;
 
    @Value("${app.whatsapp.phone-number-id}")
    private String phoneNumberId;

    @Value("${app.whatsapp.business-account-id:}")
    private String businessAccountId;
 
    @Value("${app.whatsapp.graph-api-url}")
    private String graphApiUrl;
 
    private final RestClient restClient;
 
    public WhatsappOutboundClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }
 
    public void validarConfiguracao() {
        if (!enabled || accessToken == null || accessToken.isBlank()
                || phoneNumberId == null || phoneNumberId.isBlank()) {
            throw new IllegalStateException(
                    "WhatsApp/Meta não configurado. Ative WHATSAPP_ENABLED e configure as credenciais"
            );
        }
    }

    public boolean templatesDisponiveis() {
        return enabled
                && preenchido(accessToken)
                && preenchido(phoneNumberId)
                && preenchido(businessAccountId)
                && preenchido(graphApiUrl);
    }

    public String configuracaoTemplatesKey() {
        validarConfiguracaoTemplates();
        return graphApiUrl.replaceAll("/+$", "") + "|" + businessAccountId.trim();
    }

    public TemplatePage listarTemplatesPagina(String after) {
        validarConfiguracaoTemplates();
        UriComponentsBuilder uriBuilder = UriComponentsBuilder
                .fromHttpUrl(graphApiUrl)
                .pathSegment(businessAccountId, "message_templates")
                .queryParam("fields", "id,name,language,status,category,components")
                .queryParam("limit", 100);
        if (preenchido(after)) {
            uriBuilder.queryParam("after", after);
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.get()
                    .uri(uriBuilder.build().encode().toUri())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(Map.class);
            return parseTemplatePage(response);
        } catch (RestClientResponseException exception) {
            logMetaError("consulta de templates", exception);
            throw new IllegalStateException("Nao foi possivel consultar os templates da Meta", exception);
        }
    }

    public String enviarTemplate(
            String telefoneE164,
            String nome,
            String idioma,
            List<Map<String, Object>> componentes
    ) {
        validarConfiguracaoTemplates();
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("name", nome);
        template.put("language", Map.of("code", idioma));
        if (componentes != null && !componentes.isEmpty()) {
            template.put("components", componentes);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("recipient_type", "individual");
        body.put("to", telefoneE164);
        body.put("type", "template");
        body.put("template", template);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri(graphApiUrl + "/" + phoneNumberId + "/messages")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            return extrairMensagemId(response);
        } catch (RestClientResponseException exception) {
            logMetaError("envio de template", exception);
            throw new IllegalStateException("Nao foi possivel enviar o template pela Meta", exception);
        }
    }

    private void validarConfiguracaoTemplates() {
        if (!templatesDisponiveis()) {
            throw new IllegalStateException("Templates WhatsApp/Meta nao configurados");
        }
    }

    @SuppressWarnings("unchecked")
    private TemplatePage parseTemplatePage(Map<String, Object> response) {
        if (response == null) {
            throw new IllegalStateException("Resposta inesperada ao consultar templates da Meta");
        }
        Object data = response.get("data");
        List<Map<String, Object>> templates = data instanceof List<?> list
                ? list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .toList()
                : List.of();
        String after = null;
        Object paging = response.get("paging");
        if (paging instanceof Map<?, ?> pagingMap) {
            Object cursors = pagingMap.get("cursors");
            if (cursors instanceof Map<?, ?> cursorMap) {
                after = Objects.toString(cursorMap.get("after"), null);
            }
        }
        return new TemplatePage(templates, after);
    }

    @SuppressWarnings("unchecked")
    private String extrairMensagemId(Map<String, Object> response) {
        if (response == null || !(response.get("messages") instanceof List<?> messages)
                || messages.isEmpty() || !(messages.getFirst() instanceof Map<?, ?> first)) {
            throw new IllegalStateException("Resposta inesperada da Meta API");
        }
        String id = Objects.toString(first.get("id"), "");
        if (id.isBlank()) {
            throw new IllegalStateException("Resposta da Meta sem identificador de mensagem");
        }
        return id;
    }

    public record TemplatePage(List<Map<String, Object>> templates, String after) {
        public TemplatePage {
            templates = templates == null ? List.of() : List.copyOf(templates);
        }
    }

    private boolean preenchido(String valor) {
        return valor != null && !valor.isBlank();
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
 
        try {
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
 
        } catch (RestClientResponseException e) {
            handleMetaSendError("mensagem", e);
            throw e;
        }
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
 
        try {
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
 
        } catch (RestClientResponseException e) {
            logMetaError("upload de mídia", e);
            throw e;
        }
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
 
        try {
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
 
        } catch (RestClientResponseException e) {
            handleMetaSendError("mídia", e);
            throw e;
        }
    }
 
    @SuppressWarnings("unused")
    public String enviarMidiaFallback(String telefoneE164, String tipo, String mediaId, Throwable t) {
        WhatsappTemplateRequiredException templateRequired = findTemplateRequired(t);
        if (templateRequired != null) {
            throw templateRequired;
        }
        log.error("Circuit breaker: envio de midia falhou. tipoErro={}", t.getClass().getSimpleName());
        throw new RuntimeException("Envio de midia indisponivel", t);
    }
 
    /**
     * Fallback do circuit breaker — lança exceção para que o MensagemService
     * registre a falha e publique na DLX.
     */
    @SuppressWarnings("unused")
    public String enviarTextoFallback(String telefoneE164, String corpo, Throwable t) {
        WhatsappTemplateRequiredException templateRequired = findTemplateRequired(t);
        if (templateRequired != null) {
            throw templateRequired;
        }
        log.error("Circuit breaker ativado para envio WhatsApp. tipoErro={}", t.getClass().getSimpleName());
        throw new RuntimeException("WhatsApp indisponivel (circuit breaker aberto)", t);
    }

    public record MidiaBaixada(byte[] bytes, String mimeType) {}

    /**
     * Busca os metadados da mídia na Meta e faz o download do binário.
     * Retorna um record MidiaBaixada contendo os bytes e o mimeType, ou null se falhar.
     */
    public MidiaBaixada baixarMidia(String mediaId) {
        validarConfiguracao();
        String urlMetadata = graphApiUrl + "/" + mediaId;
        log.info("Buscando metadados da mídia {} na Meta", maskId(mediaId));
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = restClient.get()
                    .uri(urlMetadata)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(Map.class);

            if (metadata == null || !metadata.containsKey("url")) {
                log.error("Metadados da mídia não contêm URL de download.");
                return null;
            }

            String urlDownload = (String) metadata.get("url");
            String mimeType = (String) metadata.get("mime_type");
            log.info("Baixando binário da mídia na Meta");

            byte[] bytes = restClient.get()
                    .uri(urlDownload)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(byte[].class);

            return new MidiaBaixada(bytes, mimeType);

        } catch (RestClientResponseException e) {
            logMetaError("download de mídia", e);
            return null;
        } catch (Exception e) {
            log.error("Erro inesperado ao baixar mídia da Meta: tipoErro={}", e.getClass().getSimpleName());
            return null;
        }
    }

    private void handleMetaSendError(String operacao, RestClientResponseException e) {
        MetaError error = logMetaError("envio de " + operacao, e);
        if (isTemplateRequired(error)) {
            throw new WhatsappTemplateRequiredException();
        }
    }

    private MetaError logMetaError(String operacao, RestClientResponseException e) {
        MetaError error = parseMetaError(e);
        log.error(
                "Erro da Meta no {}: status={}, code={}, subcode={}, type={}",
                operacao,
                e.getStatusCode().value(),
                error.code(),
                error.subcode(),
                error.type()
        );
        return error;
    }

    private MetaError parseMetaError(RestClientResponseException e) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(e.getResponseBodyAsByteArray());
            JsonNode error = root.path("error");
            return new MetaError(
                    error.path("code").asText(""),
                    error.path("error_subcode").asText(""),
                    error.path("type").asText(""),
                    error.path("message").asText("")
            );
        } catch (Exception parseException) {
            return new MetaError("", "", "", "");
        }
    }

    private boolean isTemplateRequired(MetaError error) {
        if ("131047".equals(error.code()) || "470".equals(error.code())) {
            return true;
        }
        String message = error.message().toLowerCase(Locale.ROOT);
        return message.contains("template")
                || message.contains("24 hour")
                || message.contains("24-hour")
                || message.contains("24 horas")
                || message.contains("janela")
                || message.contains("outside")
                || message.contains("re-engagement");
    }

    private WhatsappTemplateRequiredException findTemplateRequired(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof WhatsappTemplateRequiredException exception) {
                return exception;
            }
            current = current.getCause();
        }
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

    private record MetaError(String code, String subcode, String type, String message) {}
}
