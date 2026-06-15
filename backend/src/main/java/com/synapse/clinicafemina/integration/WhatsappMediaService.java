package com.synapse.clinicafemina.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * Serviço responsável por fazer o download otimizado de mídias recebidas do WhatsApp.
 * Utiliza Java Streams nativo em pequenos chunks (buffers) e o try-with-resources
 * para evitar estourar a memória (OutOfMemory) e resolver problemas de file descriptors,
 * processando o download sem carregar byte[] em memória (zero byte[] allocation).
 */
@Slf4j
@Service
public class WhatsappMediaService {

    @Value("${app.whatsapp.access-token}")
    private String accessToken;

    @Value("${app.whatsapp.graph-api-url}")
    private String graphApiUrl;

    private final RestClient restClient;

    public WhatsappMediaService() {
        this.restClient = RestClient.builder().build();
    }

    /**
     * Faz o download de uma mídia da API da Meta de forma segura usando Streams, 
     * processada por pequenos chunks de bytes nativos (via Files.copy) sem carregá-la em memória.
     * 
     * @param mediaId O ID da mídia (anexado no webhook de recebimento)
     * @param targetPath O local no disco temporário ou permanente onde salvar a mídia
     */
    public void downloadMidiaStream(String mediaId, Path targetPath) {
        log.info("Iniciando requisicao de metadados de midia WhatsApp para stream-download");

        // Passo 1: Fazer um GET para o Graph API obter a URL final da mídia
        String metadataUrl = graphApiUrl + "/" + mediaId;
        
        @SuppressWarnings("unchecked")
        Map<String, String> metadata = restClient.get()
                .uri(metadataUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(Map.class);

        if (metadata == null || !metadata.containsKey("url")) {
            throw new IllegalStateException("Nao foi possivel obter a URL da midia WhatsApp");
        }

        String downloadUrl = metadata.get("url");
        log.debug("URL final da midia recuperada, iniciando stream do binario.");

        // Passo 2: Usar o restClient .exchange() para aceder diretamente à stream da resposta
        restClient.get()
                .uri(downloadUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .exchange((request, response) -> {
                    if (response.getStatusCode().isError()) {
                        throw new IllegalStateException("Erro ao baixar binário da mídia Meta: Código HTTP " + response.getStatusCode());
                    }

                    // A leitura do InputStream e escrita para o disco em chunks é garantida
                    // pela implementação segura do Files.copy() otimizado, 
                    // sem o uso de byte[] arrays explícitos que esgotam a heap (RAM).
                    // O try-with-resources também previne o memory leak das handles da stream.
                    try (InputStream inputStream = response.getBody()) {
                        long bytesCopied = Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
                        log.info("Midia baixada com sucesso ({} bytes) e stream encerrada de forma segura", bytesCopied);
                    } catch (Exception e) {
                        log.error("Excecao ao copiar stream de midia para o disco local. tipoErro={}", e.getClass().getSimpleName());
                        throw e;
                    }
                    return null; // Interface exchange requer um objeto de retorno, nulo atende à assinatura
                });
    }
}
