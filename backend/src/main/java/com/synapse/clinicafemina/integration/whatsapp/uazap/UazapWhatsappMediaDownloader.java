package com.synapse.clinicafemina.integration.whatsapp.uazap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.clinicafemina.integration.WhatsappOutboundClient;
import com.synapse.clinicafemina.integration.whatsapp.WhatsappMediaDownloader;
import com.synapse.clinicafemina.integration.whatsapp.config.WhatsappProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Recupera mídia inbound da UAZAP em dois hops, conforme o contrato compatível com a Meta:
 * primeiro resolve o {@code mediaId} em uma URL temporária e depois baixa o binário usando o
 * mesmo Bearer da UAZAP. Nunca reutiliza o client ou as credenciais do provider Meta.
 */
@Slf4j
@Component
public class UazapWhatsappMediaDownloader implements WhatsappMediaDownloader {
    private static final int MAX_METADATA_BYTES = 64 * 1024;
    private static final int ABSOLUTE_MAX_MEDIA_BYTES = 100 * 1024 * 1024;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final WhatsappProperties.Uazap config;
    private final HostResolver hostResolver;

    @Autowired
    public UazapWhatsappMediaDownloader(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            WhatsappProperties properties
    ) {
        this(buildRestClient(builder, properties.getUazap()), objectMapper, properties, InetAddress::getAllByName);
    }

    /** Construtor de compatibilidade para testes unitários sem contexto Spring. */
    public UazapWhatsappMediaDownloader(WhatsappProperties properties) {
        this(RestClient.builder(), new ObjectMapper(), properties);
    }

    UazapWhatsappMediaDownloader(
            RestClient restClient,
            ObjectMapper objectMapper,
            WhatsappProperties properties,
            HostResolver hostResolver
    ) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.config = properties.getUazap();
        this.hostResolver = hostResolver;
    }

    private static RestClient buildRestClient(RestClient.Builder builder, WhatsappProperties.Uazap config) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.getConnectTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(config.getReadTimeoutMs()));
        return builder.clone().requestFactory(factory).build();
    }

    @Override
    public boolean supports(String phoneNumberId) {
        return preenchido(config.getPhoneNumberId()) && config.getPhoneNumberId().equals(phoneNumberId);
    }

    @Override
    public WhatsappOutboundClient.MidiaBaixada download(String mediaId) {
        if (!preenchido(mediaId)) {
            return null;
        }
        try {
            validarConfiguracao();
            UazapMediaMetadata metadata = buscarMetadados(mediaId.trim());
            URI downloadUri = validarDestino(metadata.url());
            DownloadResult arquivo = baixarBinario(downloadUri, metadata.mimeType());
            log.info("Mídia inbound UAZAP baixada. mediaId={}, tamanhoBytes={}, mimeType={}",
                    maskId(mediaId), arquivo.bytes().length, arquivo.mimeType());
            return new WhatsappOutboundClient.MidiaBaixada(arquivo.bytes(), arquivo.mimeType());
        } catch (ResourceAccessException exception) {
            log.warn("Falha de conexão ou timeout ao baixar mídia UAZAP. mediaId={}, tipoErro={}",
                    maskId(mediaId), exception.getClass().getSimpleName());
        } catch (RestClientException | IllegalArgumentException exception) {
            log.warn("Resposta inválida ao baixar mídia UAZAP. mediaId={}, tipoErro={}",
                    maskId(mediaId), exception.getClass().getSimpleName());
        } catch (MediaDownloadException exception) {
            log.warn("Mídia UAZAP não baixada. mediaId={}, motivo={}", maskId(mediaId), exception.getMessage());
        }
        return null;
    }

    private UazapMediaMetadata buscarMetadados(String mediaId) {
        return restClient.get()
                .uri(metadataUrl(mediaId))
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .accept(MediaType.APPLICATION_JSON)
                .exchange((request, response) -> {
                    validarStatus(response.getStatusCode().value(), "METADATA");
                    validarJson(response.getHeaders().getContentType());
                    byte[] body = lerLimitado(response.getBody(), MAX_METADATA_BYTES, "METADATA_EXCEDE_LIMITE");
                    UazapMediaMetadata metadata = objectMapper.readValue(body, UazapMediaMetadata.class);
                    validarMetadata(metadata, mediaId);
                    return metadata;
                });
    }

    private DownloadResult baixarBinario(URI uri, String metadataMimeType) {
        return restClient.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .header(HttpHeaders.ACCEPT, "image/*,audio/*,video/*,application/*,text/plain,text/csv")
                .exchange((request, response) -> {
                    validarStatus(response.getStatusCode().value(), "BINARIO");
                    int maxBytes = maxMediaBytes();
                    long contentLength = response.getHeaders().getContentLength();
                    if (contentLength > maxBytes) {
                        throw new MediaDownloadException("MIDIA_EXCEDE_LIMITE");
                    }
                    String responseMimeType = headerMimeType(response.getHeaders().getContentType());
                    UazapMediaContentValidator.validarHeader(responseMimeType);
                    byte[] bytes = lerLimitado(response.getBody(), maxBytes, "MIDIA_EXCEDE_LIMITE");
                    if (bytes.length == 0) {
                        throw new MediaDownloadException("MIDIA_VAZIA");
                    }
                    return new DownloadResult(
                            bytes,
                            UazapMediaContentValidator.resolver(responseMimeType, metadataMimeType, bytes));
                });
    }

    private URI validarDestino(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                    || (uri.getPort() != -1 && uri.getPort() != 443)
                    || uri.getUserInfo() != null || uri.getFragment() != null) {
                throw new MediaDownloadException("URL_DE_MIDIA_INVALIDA");
            }
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            if (!allowedHosts().contains(normalizedHost)) {
                throw new MediaDownloadException("HOST_DE_MIDIA_NAO_AUTORIZADO");
            }
            validarDnsPublico(normalizedHost);
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new MediaDownloadException("URL_DE_MIDIA_INVALIDA");
        }
    }

    private void validarDnsPublico(String host) {
        try {
            InetAddress[] addresses = hostResolver.resolve(host);
            if (addresses.length == 0 || Arrays.stream(addresses).anyMatch(this::isPrivateAddress)) {
                throw new MediaDownloadException("DESTINO_DE_MIDIA_NAO_PUBLICO");
            }
        } catch (IOException exception) {
            throw new MediaDownloadException("HOST_DE_MIDIA_INDISPONIVEL");
        }
    }

    private Set<String> allowedHosts() {
        Set<String> hosts = new HashSet<>();
        if (preenchido(config.getMediaAllowedHosts())) {
            Arrays.stream(config.getMediaAllowedHosts().split(","))
                    .map(String::trim).filter(this::preenchido)
                    .map(value -> value.toLowerCase(Locale.ROOT)).forEach(hosts::add);
        }
        try {
            String apiHost = URI.create(config.getBaseUrl()).getHost();
            if (apiHost != null) hosts.add(apiHost.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            // validarConfiguracao produz o erro sanitizado apropriado.
        }
        return Set.copyOf(hosts);
    }

    private boolean isPrivateAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return true;
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            return first == 0 || first == 127 || (first == 100 && second >= 64 && second <= 127)
                    || (first == 169 && second == 254);
        }
        return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
    }

    private void validarConfiguracao() {
        if (!preenchido(config.getBaseUrl()) || !preenchido(config.getUsername())
                || !preenchido(config.getVersion()) || !preenchido(config.getPhoneNumberId())
                || !preenchido(config.getToken())) {
            throw new MediaDownloadException("CONFIGURACAO_UAZAP_INCOMPLETA");
        }
        try {
            URI baseUri = URI.create(config.getBaseUrl());
            if (!"https".equalsIgnoreCase(baseUri.getScheme()) || baseUri.getHost() == null) {
                throw new MediaDownloadException("UAZAP_BASE_URL_INVALIDA");
            }
        } catch (IllegalArgumentException exception) {
            throw new MediaDownloadException("UAZAP_BASE_URL_INVALIDA");
        }
    }

    private void validarMetadata(UazapMediaMetadata metadata, String mediaId) {
        if (metadata == null || !preenchido(metadata.id()) || !mediaId.equals(metadata.id().trim())
                || !preenchido(metadata.url())) {
            throw new MediaDownloadException("METADATA_UAZAP_INVALIDA");
        }
    }

    private void validarJson(MediaType mediaType) {
        if (mediaType == null || !MediaType.APPLICATION_JSON.isCompatibleWith(mediaType)) {
            throw new MediaDownloadException("METADATA_CONTENT_TYPE_INVALIDO");
        }
    }

    private void validarStatus(int status, String etapa) {
        if (status < 200 || status >= 300) throw new MediaDownloadException(etapa + "_HTTP_" + status);
    }

    private byte[] lerLimitado(InputStream body, int maxBytes, String errorCode) throws IOException {
        try (InputStream input = body) {
            byte[] bytes = input.readNBytes(maxBytes + 1);
            if (bytes.length > maxBytes) throw new MediaDownloadException(errorCode);
            return bytes;
        }
    }

    private String metadataUrl(String mediaId) {
        String base = config.getBaseUrl().replaceAll("/+$", "");
        return base + "/" + UriUtils.encodePathSegment(config.getUsername(), StandardCharsets.UTF_8)
                + "/" + UriUtils.encodePathSegment(config.getVersion(), StandardCharsets.UTF_8)
                + "/" + UriUtils.encodePathSegment(mediaId, StandardCharsets.UTF_8);
    }

    private int maxMediaBytes() {
        int configured = config.getMediaMaxBytes();
        if (configured <= 0) return 25 * 1024 * 1024;
        return Math.min(configured, ABSOLUTE_MAX_MEDIA_BYTES);
    }

    private String headerMimeType(MediaType mediaType) {
        return mediaType == null ? null : mediaType.getType() + "/" + mediaType.getSubtype();
    }

    private String bearer() {
        return "Bearer " + config.getToken();
    }

    private boolean preenchido(String value) {
        return value != null && !value.isBlank();
    }

    private String maskId(String id) {
        if (!preenchido(id) || id.trim().length() <= 4) return "****";
        String trimmed = id.trim();
        return "****" + trimmed.substring(trimmed.length() - 4);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UazapMediaMetadata(String id, String url, @JsonProperty("mime_type") String mimeType) {}

    private record DownloadResult(byte[] bytes, String mimeType) {}

    static final class MediaDownloadException extends RuntimeException {
        MediaDownloadException(String code) {
            super(code);
        }
    }

    @FunctionalInterface
    interface HostResolver {
        InetAddress[] resolve(String host) throws IOException;
    }
}
