package com.synapse.clinicafemina.integration.whatsapp.uazap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.clinicafemina.integration.WhatsappOutboundClient;
import com.synapse.clinicafemina.integration.whatsapp.config.WhatsappProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("UazapWhatsappMediaDownloader — mídia inbound em dois hops")
class UazapWhatsappMediaDownloaderTest {

    private static final String METADATA_URL = "https://uazap.test/user/v2/media-1";
    private static final String DOWNLOAD_URL = "https://media.uazap.test/file?signature=valor-secreto";

    private MockRestServiceServer server;
    private WhatsappProperties properties;
    private UazapWhatsappMediaDownloader downloader;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        properties = configuredProperties();
        downloader = new UazapWhatsappMediaDownloader(
                builder.build(),
                new ObjectMapper(),
                properties,
                host -> new InetAddress[] {InetAddress.getByName("8.8.8.8")}
        );
    }

    @ParameterizedTest(name = "baixa {0}")
    @MethodSource("supportedMedia")
    @DisplayName("busca metadados e baixa imagem, áudio, documento e vídeo com o mesmo Bearer")
    void downloadsSupportedMedia(String label, String mimeType, byte[] bytes) {
        expectMetadata("media-1", DOWNLOAD_URL, mimeType);
        server.expect(requestTo(DOWNLOAD_URL))
                .andExpect(method(GET))
                .andExpect(header(AUTHORIZATION, "Bearer secret-token"))
                .andRespond(withSuccess(bytes, MediaType.parseMediaType(mimeType)));

        WhatsappOutboundClient.MidiaBaixada result = downloader.download("media-1");

        assertThat(result).isNotNull();
        assertThat(result.bytes()).isEqualTo(bytes);
        assertThat(result.mimeType()).isEqualTo(mimeType);
        server.verify();
    }

    @Test
    @DisplayName("usa mime_type dos metadados quando o binário não informa Content-Type")
    void usesMetadataMimeTypeWhenBinaryHeaderIsMissing() {
        byte[] pdf = "%PDF-1.7".getBytes();
        expectMetadata("media-1", DOWNLOAD_URL, "application/pdf");
        server.expect(requestTo(DOWNLOAD_URL))
                .andRespond(withSuccess().body(pdf));

        WhatsappOutboundClient.MidiaBaixada result = downloader.download("media-1");

        assertThat(result).isNotNull();
        assertThat(result.mimeType()).isEqualTo("application/pdf");
        server.verify();
    }

    @Test
    @DisplayName("recusa resposta HTML mesmo quando a etapa de metadados teve sucesso")
    void rejectsInvalidContentType() {
        expectMetadata("media-1", DOWNLOAD_URL, "image/jpeg");
        server.expect(requestTo(DOWNLOAD_URL))
                .andRespond(withSuccess("<html>erro</html>", MediaType.TEXT_HTML));

        assertThat(downloader.download("media-1")).isNull();
        server.verify();
    }

    @Test
    @DisplayName("interrompe o download quando Content-Length excede o limite configurado")
    void rejectsContentLargerThanConfiguredLimit() {
        properties.getUazap().setMediaMaxBytes(4);
        expectMetadata("media-1", DOWNLOAD_URL, "video/mp4");
        server.expect(requestTo(DOWNLOAD_URL))
                .andRespond(withSuccess(new byte[5], MediaType.parseMediaType("video/mp4")));

        assertThat(downloader.download("media-1")).isNull();
        server.verify();
    }

    @Test
    @DisplayName("não envia o Bearer para host fora da allowlist")
    void rejectsUntrustedDownloadHostBeforeSecondRequest() {
        expectMetadata("media-1", "https://untrusted.test/file", "image/jpeg");

        assertThat(downloader.download("media-1")).isNull();
        server.verify();
    }

    @Test
    @DisplayName("HTTP 4xx/5xx da UAZAP retorna null sem propagar exceção")
    void handlesHttpFailureWithoutThrowing() {
        server.expect(requestTo(METADATA_URL))
                .andRespond(withStatus(HttpStatusCode.valueOf(503)));

        assertThat(downloader.download("media-1")).isNull();
        server.verify();
    }

    @Test
    @DisplayName("timeout da UAZAP retorna null sem propagar exceção")
    void handlesTimeoutWithoutThrowing() {
        server.expect(requestTo(METADATA_URL))
                .andRespond(request -> {
                    throw new SocketTimeoutException("simulado");
                });

        assertThat(downloader.download("media-1")).isNull();
        server.verify();
    }

    private void expectMetadata(String id, String url, String mimeType) {
        String body = "{\"id\":\"" + id + "\",\"url\":\"" + url
                + "\",\"mime_type\":\"" + mimeType + "\"}";
        server.expect(requestTo(METADATA_URL))
                .andExpect(method(GET))
                .andExpect(header(AUTHORIZATION, "Bearer secret-token"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private WhatsappProperties configuredProperties() {
        WhatsappProperties result = new WhatsappProperties();
        WhatsappProperties.Uazap uazap = result.getUazap();
        uazap.setBaseUrl("https://uazap.test");
        uazap.setUsername("user");
        uazap.setVersion("v2");
        uazap.setPhoneNumberId("instance-1");
        uazap.setToken("secret-token");
        uazap.setMediaAllowedHosts("media.uazap.test");
        return result;
    }

    private static Stream<Arguments> supportedMedia() {
        return Stream.of(
                Arguments.of("imagem", "image/jpeg", new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1}),
                Arguments.of("áudio", "audio/ogg", "OggS-audio".getBytes()),
                Arguments.of("documento", "application/pdf", "%PDF-doc".getBytes()),
                Arguments.of("vídeo", "video/mp4", new byte[] {0, 0, 0, 20, 'f', 't', 'y', 'p', 1})
        );
    }
}
