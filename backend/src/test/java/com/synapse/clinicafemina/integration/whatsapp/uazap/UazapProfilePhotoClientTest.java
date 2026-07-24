package com.synapse.clinicafemina.integration.whatsapp.uazap;

import com.synapse.clinicafemina.integration.whatsapp.config.WhatsappProperties;
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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("UazapProfilePhotoClient — consulta getPicture (mock HTTP local, sem rede externa)")
class UazapProfilePhotoClientTest {

    private static final String CONTACTS_URL = "https://uazap.test/user/v2/inst-1/contacts";

    private MockRestServiceServer server;
    private UazapProfilePhotoClient client;

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

        client = new UazapProfilePhotoClient(restClient, properties);
    }

    @Test
    @DisplayName("monta URL/Bearer/body getPicture e devolve status/content-type/corpo crus em caso de sucesso")
    void buscarFotoPerfil_success_returnsRawResponse() {
        server.expect(requestTo(CONTACTS_URL))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header(AUTHORIZATION, "Bearer secret-token"))
                .andExpect(jsonPath("$.type").value("contacts"))
                .andExpect(jsonPath("$.action").value("getPicture"))
                .andExpect(jsonPath("$.contacts.to").value("5511999999999"))
                .andRespond(withSuccess("{\"url\":\"https://cdn.example/x\"}", MediaType.APPLICATION_JSON));

        UazapPictureRawResponse response = client.buscarFotoPerfil("+55 (11) 99999-9999");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.contentType()).contains("application/json");
        assertThat(new String(response.body())).contains("cdn.example");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 404, 429, 500})
    @DisplayName("status de erro HTTP é devolvido cru (não lança exceção) para o diagnóstico poder reportar")
    void buscarFotoPerfil_errorStatus_returnsRawResponseNotException(int status) {
        server.expect(requestTo(CONTACTS_URL))
                .andRespond(withStatus(HttpStatusCode.valueOf(status)));

        UazapPictureRawResponse response = client.buscarFotoPerfil("5511999999999");

        assertThat(response.statusCode()).isEqualTo(status);
    }

    @Test
    @DisplayName("resposta vazia é devolvida como corpo de 0 bytes")
    void buscarFotoPerfil_emptyBody_returnsZeroLengthBody() {
        server.expect(requestTo(CONTACTS_URL))
                .andRespond(withSuccess());

        UazapPictureRawResponse response = client.buscarFotoPerfil("5511999999999");

        assertThat(response.body()).isEmpty();
    }

    @Test
    @DisplayName("timeout/I-O gera UazapException")
    void buscarFotoPerfil_timeout_throwsUazapException() {
        server.expect(requestTo(CONTACTS_URL))
                .andRespond(request -> {
                    throw new SocketTimeoutException("simulado");
                });

        assertThatThrownBy(() -> client.buscarFotoPerfil("5511999999999"))
                .isInstanceOf(UazapException.class);
    }
}
