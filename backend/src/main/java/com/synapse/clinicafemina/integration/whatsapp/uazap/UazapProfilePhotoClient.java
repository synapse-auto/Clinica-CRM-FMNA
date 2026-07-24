package com.synapse.clinicafemina.integration.whatsapp.uazap;

import com.synapse.clinicafemina.integration.whatsapp.config.WhatsappProperties;
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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cliente HTTP isolado, exclusivo da UAZAP, para consultar a foto de perfil de um contato.
 *
 * <p>Endpoint: {@code POST {baseUrl}/{username}/{version}/{phoneNumberId}/contacts} com
 * {@code {"type":"contacts","action":"getPicture","contacts":{"to":"<telefone>"}}}. O schema da
 * resposta NÃO é documentado no OpenAPI oficial da UAZAP (HTTP 201 com {@code content: {}}) —
 * por isso este cliente devolve a resposta <strong>crua</strong> (status, content-type, bytes),
 * sem tentar interpretá-la. A interpretação fica isolada em {@link UazapPicturePayloadParser}.</p>
 *
 * <p>Nunca reaproveita credenciais Meta. Reutiliza exclusivamente
 * {@link WhatsappProperties.Uazap} (mesmas variáveis {@code UAZAP_*} já usadas por
 * {@link UazapClient}). Logs nunca registram token, corpo integral, telefone completo ou URL.</p>
 */
@Slf4j
@Component
public class UazapProfilePhotoClient {

    private final RestClient restClient;
    private final WhatsappProperties.Uazap config;

    @Autowired
    public UazapProfilePhotoClient(RestClient.Builder builder, WhatsappProperties properties) {
        this(buildRestClient(builder, properties.getUazap()), properties);
    }

    /** Construtor visível para testes: injeta um {@link RestClient} já vinculado (ex.: MockRestServiceServer). */
    UazapProfilePhotoClient(RestClient restClient, WhatsappProperties properties) {
        this.restClient = restClient;
        this.config = properties.getUazap();
    }

    private static RestClient buildRestClient(RestClient.Builder builder, WhatsappProperties.Uazap uazap) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(uazap.getConnectTimeoutMs());
        factory.setReadTimeout(uazap.getReadTimeoutMs());
        return builder.clone().requestFactory(factory).build();
    }

    /**
     * Consulta a foto de perfil do contato. Devolve a resposta crua mesmo em caso de status de
     * erro HTTP (o chamador decide o que fazer). Lança {@link UazapException} apenas para falhas
     * de infraestrutura (timeout, conexão recusada, resposta ilegível).
     */
    public UazapPictureRawResponse buscarFotoPerfil(String toE164) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "contacts");
        payload.put("action", "getPicture");
        payload.put("contacts", Map.of("to", onlyDigits(toE164)));

        try {
            return restClient.post()
                    .uri(contactsUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.getToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .exchange((request, response) -> {
                        byte[] body = response.getBody() != null ? response.getBody().readAllBytes() : new byte[0];
                        String contentType = response.getHeaders().getContentType() == null
                                ? null
                                : response.getHeaders().getContentType().toString();
                        return new UazapPictureRawResponse(response.getStatusCode().value(), contentType, body);
                    });
        } catch (ResourceAccessException exception) {
            log.warn("Falha de I/O ao consultar foto de perfil UAZAP. tipoErro={}", exception.getClass().getSimpleName());
            throw new UazapException("Falha de conexão ou timeout ao consultar foto de perfil UAZAP", exception);
        } catch (RestClientException exception) {
            log.warn("Erro inesperado ao consultar foto de perfil UAZAP. tipoErro={}", exception.getClass().getSimpleName());
            throw new UazapException("Erro inesperado ao consultar foto de perfil UAZAP", exception);
        }
    }

    private String contactsUrl() {
        String base = config.getBaseUrl() == null ? "" : config.getBaseUrl().replaceAll("/+$", "");
        return base + "/" + config.getUsername() + "/" + config.getVersion()
                + "/" + config.getPhoneNumberId() + "/contacts";
    }

    private static String onlyDigits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }
}
