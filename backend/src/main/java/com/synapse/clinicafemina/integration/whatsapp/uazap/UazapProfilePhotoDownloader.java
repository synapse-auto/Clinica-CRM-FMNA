package com.synapse.clinicafemina.integration.whatsapp.uazap;

import com.synapse.clinicafemina.integration.whatsapp.config.WhatsappProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Component
public class UazapProfilePhotoDownloader {

    private static final int MAX_DOWNLOAD_BYTES = UazapProfilePhotoImageValidator.MAX_IMAGE_BYTES;

    private final HttpClient httpClient;
    private final UazapProfilePhotoImageValidator imageValidator;
    private final Set<String> allowedHosts;
    private final HostResolver hostResolver;
    private final Duration requestTimeout;

    @Autowired
    public UazapProfilePhotoDownloader(
            WhatsappProperties properties,
            UazapProfilePhotoImageValidator imageValidator
    ) {
        this(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(properties.getUazap().getConnectTimeoutMs()))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                imageValidator,
                resolveAllowedHosts(properties.getUazap()),
                InetAddress::getAllByName,
                Duration.ofMillis(properties.getUazap().getReadTimeoutMs())
        );
    }

    UazapProfilePhotoDownloader(
            HttpClient httpClient,
            UazapProfilePhotoImageValidator imageValidator,
            Set<String> allowedHosts,
            HostResolver hostResolver,
            Duration requestTimeout
    ) {
        this.httpClient = httpClient;
        this.imageValidator = imageValidator;
        this.allowedHosts = Set.copyOf(allowedHosts);
        this.hostResolver = hostResolver;
        this.requestTimeout = requestTimeout;
    }

    public UazapProfilePhotoImageValidator.ValidatedImage baixar(URI uri) {
        validarDestino(uri);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Accept", "image/jpeg,image/png,image/webp")
                .GET()
                .build();

        try {
            HttpResponse<InputStream> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            if (status == 404 || status == 410) {
                fechar(response.body());
                throw UazapProfilePhotoDownloadException.semFoto("FOTO_NAO_ENCONTRADA");
            }
            if (status == 429 || status >= 500) {
                fechar(response.body());
                throw UazapProfilePhotoDownloadException.temporaria("FALHA_HTTP_FOTO_" + status);
            }
            if (status < 200 || status >= 300) {
                fechar(response.body());
                throw UazapProfilePhotoDownloadException.permanente("FALHA_HTTP_FOTO_" + status);
            }

            byte[] bytes;
            try (InputStream body = response.body()) {
                bytes = body.readNBytes(MAX_DOWNLOAD_BYTES + 1);
            }
            if (bytes.length > MAX_DOWNLOAD_BYTES) {
                throw UazapProfilePhotoDownloadException.permanente("IMAGEM_EXCEDE_LIMITE");
            }
            String contentType = response.headers().firstValue("Content-Type").orElse(null);
            try {
                return imageValidator.validar(bytes, contentType);
            } catch (IllegalArgumentException exception) {
                throw UazapProfilePhotoDownloadException.permanente(exception.getMessage());
            }
        } catch (UazapProfilePhotoDownloadException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw UazapProfilePhotoDownloadException.temporaria("DOWNLOAD_INTERROMPIDO");
        } catch (IOException exception) {
            throw UazapProfilePhotoDownloadException.temporaria("FALHA_DE_COMUNICACAO_DA_FOTO");
        }
    }

    private void validarDestino(URI uri) {
        String host = uri == null ? null : uri.getHost();
        if (uri == null
                || !"https".equalsIgnoreCase(uri.getScheme())
                || host == null
                || (uri.getPort() != -1 && uri.getPort() != 443)
                || uri.getUserInfo() != null
                || uri.getFragment() != null) {
            throw UazapProfilePhotoDownloadException.permanente("URL_DE_FOTO_INVALIDA");
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (!allowedHosts.contains(normalizedHost)) {
            throw UazapProfilePhotoDownloadException.permanente("HOST_DE_FOTO_NAO_AUTORIZADO");
        }
        try {
            InetAddress[] addresses = hostResolver.resolve(normalizedHost);
            if (addresses.length == 0 || Arrays.stream(addresses).anyMatch(this::isPrivateAddress)) {
                throw UazapProfilePhotoDownloadException.permanente("DESTINO_DE_FOTO_NAO_PUBLICO");
            }
        } catch (UazapProfilePhotoDownloadException exception) {
            throw exception;
        } catch (IOException exception) {
            throw UazapProfilePhotoDownloadException.temporaria("HOST_DE_FOTO_INDISPONIVEL");
        }
    }

    private boolean isPrivateAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            return first == 0
                    || first == 127
                    || (first == 100 && second >= 64 && second <= 127)
                    || (first == 169 && second == 254);
        }
        return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
    }

    static Set<String> resolveAllowedHosts(WhatsappProperties.Uazap config) {
        Set<String> hosts = new HashSet<>();
        if (config.getPictureAllowedHosts() != null) {
            Arrays.stream(config.getPictureAllowedHosts().split(","))
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .map(value -> value.toLowerCase(Locale.ROOT))
                    .forEach(hosts::add);
        }
        return hosts;
    }

    private static void fechar(InputStream body) {
        if (body == null) {
            return;
        }
        try {
            body.close();
        } catch (IOException ignored) {
            // Nao ha acao util ao falhar no fechamento de um corpo descartado.
        }
    }

    @FunctionalInterface
    interface HostResolver {
        InetAddress[] resolve(String host) throws IOException;
    }
}
