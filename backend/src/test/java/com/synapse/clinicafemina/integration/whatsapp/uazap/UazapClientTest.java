package com.synapse.clinicafemina.integration.whatsapp.uazap;

import com.synapse.clinicafemina.integration.whatsapp.WhatsappProviderType;
import com.synapse.clinicafemina.integration.whatsapp.config.WhatsappProperties;
import com.synapse.clinicafemina.integration.whatsapp.model.WhatsappMessageType;
import com.synapse.clinicafemina.integration.whatsapp.model.WhatsappSendResult;
import com.synapse.clinicafemina.integration.whatsapp.uazap.exception.UazapException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("UazapClient — envio outbound isolado (mock HTTP local, sem rede externa)")
class UazapClientTest {

    private static final String MESSAGES_URL = "https://uazap.test/user/v2/inst-1/messages";
    private static final String MEDIA_URL = "https://uazap.test/user/v2/inst-1/media";

    private MockRestServiceServer server;
    private UazapClient client;
    private WhatsappProperties properties;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        properties = new WhatsappProperties();
        properties.setEnabled(true);
        properties.setProvider("UAZAP");
        WhatsappProperties.Uazap uazap = properties.getUazap();
        uazap.setBaseUrl("https://uazap.test");
        uazap.setUsername("user");
        uazap.setVersion("v2");
        uazap.setPhoneNumberId("inst-1");
        uazap.setToken("secret-token");

        client = new UazapClient(restClient, properties);
    }

    @Test
    @DisplayName("contrato Uzapi: envia uma vez, usa wamid e confirma o wa_id resolvido")
    void sendText_success_usesOfficialWamidAndConfirmedRecipient() {
        server.expect(requestTo(MESSAGES_URL))
                .andExpect(method(POST))
                .andExpect(header(AUTHORIZATION, "Bearer secret-token"))
                .andExpect(jsonPath("$.to").value("5511999999999"))
                .andExpect(jsonPath("$.delayMessage").value(0))
                .andExpect(jsonPath("$.delayTyping").value(0))
                .andExpect(jsonPath("$.type").value("text"))
                .andExpect(jsonPath("$.text.body").value("Olá"))
                .andRespond(withSuccess(
                        """
                                {"status":"success","message":"Mensagem colocada na fila de envios com sucesso!",
                                "queueId":"QUEUE-123","messageId":"INTERNO-456",
                                "contacts":[{"input":"5511999999999","wa_id":"5511999999999"}],
                                "messages":[{"id":"wamid.TESTE"}]}
                                """,
                        MediaType.APPLICATION_JSON));

        WhatsappSendResult result = client.sendText("+55 (11) 99999-9999", "Olá");

        assertThat(result.externalMessageId()).isEqualTo("wamid.TESTE");
        assertThat(result.externalMessageId()).isNotEqualTo("INTERNO-456");
        assertThat(result.externalMessageId()).isNotEqualTo("QUEUE-123");
        assertThat(result.confirmedRecipient()).isEqualTo("5511999999999");
        assertThat(result.provider()).isEqualTo(WhatsappProviderType.UAZAP);
        server.verify();
    }

    @Test
    @DisplayName("uploadMedia envia multipart para o endpoint oficial e retorna o media ID")
    void uploadMedia_usesOfficialMultipartEndpoint() {
        server.expect(requestTo(MEDIA_URL))
                .andExpect(method(POST))
                .andExpect(header(AUTHORIZATION, "Bearer secret-token"))
                .andExpect(request -> assertThat(request.getHeaders().getContentType()).isNotNull()
                        .extracting(MediaType::getType).isEqualTo("multipart"))
                .andRespond(withStatus(HttpStatusCode.valueOf(201))
                        .body("{\"id\":\"media-pdf-1\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        String mediaId = client.uploadMedia(
                new MockMultipartFile("file", "guia.pdf", "application/pdf", "pdf".getBytes()).getResource(),
                "application/pdf",
                "guia.pdf"
        );

        assertThat(mediaId).isEqualTo("media-pdf-1");
        server.verify();
    }

    @Test
    @DisplayName("sendMedia usa id quando a referência veio do upload")
    void sendMedia_usesIdForUploadedMedia() {
        server.expect(requestTo(MESSAGES_URL))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.type").value("document"))
                .andExpect(jsonPath("$.document.id").value("media-pdf-1"))
                .andRespond(withSuccess(
                        "{\"status\":\"success\",\"queueId\":\"QUEUE-10\",\"messageId\":\"INTERNO-10\","
                                + "\"messages\":[{\"id\":\"wamid.PDF\"}]}",
                        MediaType.APPLICATION_JSON));

        WhatsappSendResult result = client.sendMedia(
                "5511999999999", WhatsappMessageType.DOCUMENT, "media-pdf-1", null);

        assertThat(result.externalMessageId()).isEqualTo("wamid.PDF");
        server.verify();
    }

    @Test
    @DisplayName("sendMedia mapeia type e bloco link/caption do provider")
    void sendMedia_mapsBody() {
        server.expect(requestTo(MESSAGES_URL))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.type").value("image"))
                .andExpect(jsonPath("$.image.link").value("https://cdn.test/x.jpg"))
                .andExpect(jsonPath("$.image.caption").value("legenda"))
                .andRespond(withSuccess(
                        "{\"status\":\"success\",\"queueId\":\"QUEUE-9\",\"messageId\":\"INTERNO-9\","
                                + "\"messages\":[{\"id\":\"wamid.MEDIA\"}]}",
                        MediaType.APPLICATION_JSON));

        WhatsappSendResult result = client.sendMedia(
                "5511999999999", WhatsappMessageType.IMAGE, "https://cdn.test/x.jpg", "legenda");

        assertThat(result.externalMessageId()).isEqualTo("wamid.MEDIA");
        server.verify();
    }

    @Test
    @DisplayName("resposta de sucesso sem wamid nao e aceita como envio comprovado")
    void successfulResponseWithoutWamid_throws() {
        server.expect(requestTo(MESSAGES_URL))
                .andRespond(withSuccess(
                        "{\"status\":\"success\",\"queueId\":\"QUEUE-123\",\"messageId\":\"INTERNO-456\",\"messages\":[]}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.sendText("5511999999999", "oi"))
                .isInstanceOf(UazapException.class)
                .hasMessageContaining("WhatsApp");
    }

    @Test
    @DisplayName("status logico de erro nao e aceito mesmo com HTTP 200")
    void logicalErrorResponse_throws() {
        server.expect(requestTo(MESSAGES_URL))
                .andRespond(withSuccess(
                        "{\"status\":\"error\",\"message\":\"Instancia desconectada\",\"queueId\":null,\"messageId\":null,\"contacts\":[],\"messages\":[]}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.sendText("5511999999999", "oi"))
                .isInstanceOf(UazapException.class);
    }

    @Test
    @DisplayName("falha antes do HTTP quando o provider configurado nao e UAZAP")
    void providerOtherThanUazap_failsFast() {
        properties.setProvider("META");

        assertThatThrownBy(() -> client.sendText("5511999999999", "oi"))
                .isInstanceOf(UazapException.class)
                .hasMessageContaining("WHATSAPP_PROVIDER");
    }

    @Test
    @DisplayName("falha antes do HTTP quando WHATSAPP_ENABLED esta desabilitado")
    void disabledWhatsapp_failsFast() {
        properties.setEnabled(false);

        assertThatThrownBy(() -> client.sendText("5511999999999", "oi"))
                .isInstanceOf(UazapException.class)
                .hasMessageContaining("WHATSAPP_ENABLED");
    }

    @Test
    @DisplayName("falha antes do HTTP quando UAZAP_TOKEN esta ausente")
    void missingToken_failsFast() {
        properties.getUazap().setToken(" ");

        assertThatThrownBy(() -> client.sendText("5511999999999", "oi"))
                .isInstanceOf(UazapException.class)
                .hasMessageContaining("UAZAP_TOKEN");
    }

    @Test
    @DisplayName("corpo de resposta inválido gera UazapException")
    void invalidBody_throws() {
        server.expect(requestTo(MESSAGES_URL))
                .andRespond(withSuccess("isto-nao-e-json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.sendText("5511999999999", "oi"))
                .isInstanceOf(UazapException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 409, 429, 500, 503})
    @DisplayName("status HTTP de erro gera UazapException com o status na mensagem")
    void errorStatus_throwsWithStatus(int status) {
        server.expect(requestTo(MESSAGES_URL))
                .andRespond(withStatus(HttpStatusCode.valueOf(status)));

        assertThatThrownBy(() -> client.sendText("5511999999999", "oi"))
                .isInstanceOf(UazapException.class)
                .hasMessageContaining(String.valueOf(status));
    }

    @Test
    @DisplayName("timeout/I-O gera UazapException de conexão")
    void timeout_throws() {
        server.expect(requestTo(MESSAGES_URL))
                .andRespond(request -> {
                    throw new SocketTimeoutException("simulado");
                });

        assertThatThrownBy(() -> client.sendText("5511999999999", "oi"))
                .isInstanceOf(UazapException.class)
                .hasMessageContaining("timeout");
    }
}
