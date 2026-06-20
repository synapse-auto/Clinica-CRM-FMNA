package com.synapse.clinicafemina.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

@Slf4j
@Service
public class WhatsappMediaService {

    @Value("${app.whatsapp.enabled:false}")
    private boolean enabled;

    @Value("${app.whatsapp.access-token}")
    private String accessToken;

    @Value("${app.whatsapp.graph-api-url}")
    private String graphApiUrl;

    private final RestClient restClient = RestClient.builder().build();

    public void copiarPara(String mediaId, OutputStream destino) {
        validarConfiguracao();
        String downloadUrl = obterUrlDownload(mediaId);
        restClient.get()
                .uri(downloadUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .exchange((request, response) -> {
                    if (response.getStatusCode().isError()) {
                        throw new IllegalStateException("A Meta recusou o download da mídia");
                    }
                    try (InputStream input = response.getBody()) {
                        input.transferTo(destino);
                        destino.flush();
                    }
                    return null;
                });
    }

    private String obterUrlDownload(String mediaId) {
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = restClient.get()
                .uri(graphApiUrl + "/" + mediaId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(Map.class);
        if (metadata == null || !(metadata.get("url") instanceof String url) || url.isBlank()) {
            throw new IllegalStateException("A Meta não retornou a URL da mídia");
        }
        return url;
    }

    private void validarConfiguracao() {
        if (!enabled || accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("WhatsApp/Meta não configurado para download de mídia");
        }
    }
}
