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

    private MockRestServiceServer server;
    private UazapClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        WhatsappProperties properties = new WhatsappProperties();
        WhatsappProperties.Uazap uazap = properties.getUazap();
        uazap.setBaseUrl("https://uazap.test");
        uazap.setUsername("user");
        uazap.setVersion("v2");
        uazap.setPhoneNumberId("inst-1");
        uazap.setToken("secret-token");

        client = new UazapClient(restClient, properties);
    }

    @Test
    @DisplayName("sendText monta URL/Bearer/body e extrai messageId como externalMessageId")
    void sendText_success_extractsMessageId() {
        server.expect(requestTo(MESSAGES_URL))
                .andExpect(method(POST))
                .andExpect(header(AUTHORIZATION, "Bearer secret-token"))
                .andExpect(jsonPath("$.to").value("5511999999999"))
                .andExpect(jsonPath("$.type").value("text"))
                .andExpect(jsonPath("$.text.body").value("Olá"))
                .andRespond(withSuccess(
                        "{\"statusCode\":200,\"message\":\"ok\",\"queueId\":\"q1\",\"messageId\":\"UZ-123\"}",
                        MediaType.APPLICATION_JSON));

        WhatsappSendResult result = client.sendText("+55 (11) 99999-9999", "Olá");

        assertThat(result.externalMessageId()).isEqualTo("UZ-123");
        assertThat(result.provider()).isEqualTo(WhatsappProviderType.UAZAP);
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
                        "{\"messageId\":\"UZ-9\"}", MediaType.APPLICATION_JSON));

        WhatsappSendResult result = client.sendMedia(
                "5511999999999", WhatsappMessageType.IMAGE, "https://cdn.test/x.jpg", "legenda");

        assertThat(result.externalMessageId()).isEqualTo("UZ-9");
        server.verify();
    }

    @Test
    @DisplayName("resposta sem messageId gera UazapException")
    void missingMessageId_throws() {
        server.expect(requestTo(MESSAGES_URL))
                .andRespond(withSuccess("{\"statusCode\":200}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.sendText("5511999999999", "oi"))
                .isInstanceOf(UazapException.class)
                .hasMessageContaining("sem messageId");
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
