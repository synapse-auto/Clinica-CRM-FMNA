package com.synapse.clinicafemina.integration.whatsapp.uazap;

import com.synapse.clinicafemina.integration.whatsapp.config.WhatsappProperties;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class UazapProfilePhotoDownloaderTest {

    @Test
    void emptyConfiguredAllowlistDoesNotImplicitlyAuthorizeTheApiHost() {
        WhatsappProperties.Uazap config = new WhatsappProperties.Uazap();
        config.setBaseUrl("https://api.uazap.example");
        config.setPictureAllowedHosts("");

        assertThat(UazapProfilePhotoDownloader.resolveAllowedHosts(config)).isEmpty();
    }

    @Test
    void wildcardDoesNotAuthorizeSubdomains() {
        UazapProfilePhotoDownloader downloader = new UazapProfilePhotoDownloader(
                mock(HttpClient.class),
                new UazapProfilePhotoImageValidator(),
                Set.of("*.uazap.example"),
                host -> new InetAddress[] {InetAddress.getByName("8.8.8.8")},
                Duration.ofSeconds(2)
        );

        assertThatThrownBy(() -> downloader.baixar(URI.create("https://cdn.uazap.example/photo.jpg")))
                .isInstanceOf(UazapProfilePhotoDownloadException.class)
                .hasMessage("HOST_DE_FOTO_NAO_AUTORIZADO");
    }

    @Test
    void rejectsHostOutsideExplicitAllowlistBeforeHttpRequest() throws Exception {
        HttpClient client = mock(HttpClient.class);
        UazapProfilePhotoDownloader downloader = new UazapProfilePhotoDownloader(
                client,
                new UazapProfilePhotoImageValidator(),
                Set.of("cdn.uazap.example"),
                host -> new InetAddress[] {InetAddress.getByName("8.8.8.8")},
                Duration.ofSeconds(2)
        );

        assertThatThrownBy(() -> downloader.baixar(URI.create("https://evil.example/photo.jpg")))
                .isInstanceOf(UazapProfilePhotoDownloadException.class)
                .hasMessage("HOST_DE_FOTO_NAO_AUTORIZADO");
        verify(client, never()).send(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.<java.net.http.HttpResponse.BodyHandler<Object>>any()
        );
    }

    @Test
    void rejectsAllowedHostWhenDnsResolvesToPrivateAddress() {
        HttpClient client = mock(HttpClient.class);
        UazapProfilePhotoDownloader downloader = new UazapProfilePhotoDownloader(
                client,
                new UazapProfilePhotoImageValidator(),
                Set.of("cdn.uazap.example"),
                host -> new InetAddress[] {InetAddress.getByName("127.0.0.1")},
                Duration.ofSeconds(2)
        );

        assertThatThrownBy(() -> downloader.baixar(URI.create("https://cdn.uazap.example/photo.jpg")))
                .isInstanceOf(UazapProfilePhotoDownloadException.class)
                .hasMessage("DESTINO_DE_FOTO_NAO_PUBLICO");
    }

    @Test
    void rejectsHttpEvenForAllowedHost() {
        UazapProfilePhotoDownloader downloader = new UazapProfilePhotoDownloader(
                mock(HttpClient.class),
                new UazapProfilePhotoImageValidator(),
                Set.of("cdn.uazap.example"),
                host -> new InetAddress[] {InetAddress.getByName("8.8.8.8")},
                Duration.ofSeconds(2)
        );

        assertThatThrownBy(() -> downloader.baixar(URI.create("http://cdn.uazap.example/photo.jpg")))
                .isInstanceOf(UazapProfilePhotoDownloadException.class)
                .hasMessage("URL_DE_FOTO_INVALIDA");
    }

    @Test
    void rejectsNonStandardPortEvenForAllowedHost() {
        UazapProfilePhotoDownloader downloader = new UazapProfilePhotoDownloader(
                mock(HttpClient.class),
                new UazapProfilePhotoImageValidator(),
                Set.of("cdn.uazap.example"),
                host -> new InetAddress[] {InetAddress.getByName("8.8.8.8")},
                Duration.ofSeconds(2)
        );

        assertThatThrownBy(() -> downloader.baixar(
                URI.create("https://cdn.uazap.example:8443/photo.jpg")
        ))
                .isInstanceOf(UazapProfilePhotoDownloadException.class)
                .hasMessage("URL_DE_FOTO_INVALIDA");
    }
}
