package com.synapse.clinicafemina.integration;

import com.synapse.clinicafemina.dto.darwin.DarwinPageResponse;
import com.synapse.clinicafemina.dto.darwin.DarwinPatientDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * DarwinClient — GET-only, autenticação Bearer, propagação de erros HTTP (sem captura própria;
 * quem trata é ExternalSyncService, já coberto por ExternalSyncServiceTest).
 */
@DisplayName("DarwinClient — contrato read-only da API Darwin")
class DarwinClientTest {

    private MockRestServiceServer server;
    private DarwinClient client;

    @BeforeEach
    void setUp() throws Exception {
        client = new DarwinClient("https://darwin.test", "secret-darwin-token");
        RestClient.Builder builder = RestClient.builder().baseUrl("https://darwin.test")
                .defaultHeader(AUTHORIZATION, "Bearer secret-darwin-token");
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient mockedRestClient = builder.build();

        Field restClientField = DarwinClient.class.getDeclaredField("restClient");
        restClientField.setAccessible(true);
        restClientField.set(client, mockedRestClient);
    }

    @Test
    @DisplayName("getPatients envia Bearer, updated_after em UTC, cursor e limit; parseia a página")
    void getPatients_sendsAuthAndQueryParams_parsesPage() {
        OffsetDateTime updatedAfter = OffsetDateTime.parse("2026-01-10T12:00:00-03:00");
        server.expect(requestToUriTemplate(
                        "https://darwin.test/v1/patients?updated_after={ua}&cursor={cursor}&limit={limit}",
                        "2026-01-10T15:00Z", "cur-1", "50"))
                .andExpect(method(GET))
                .andExpect(header(AUTHORIZATION, "Bearer secret-darwin-token"))
                .andRespond(withSuccess(
                        "{\"data\":[],\"hasMore\":false,\"nextCursor\":null}", MediaType.APPLICATION_JSON));

        DarwinPageResponse<DarwinPatientDTO> response = client.getPatients(updatedAfter, "cur-1", 50);

        assertThat(response.hasMore()).isFalse();
        server.verify();
    }

    @Test
    @DisplayName("getPatients sem updated_after/cursor omite os parâmetros opcionais")
    void getPatients_withoutOptionalParams_omitsThem() {
        // requestToUriTemplate exige URI EXATA — se updated_after/cursor fossem enviados, o teste falharia aqui.
        server.expect(requestToUriTemplate("https://darwin.test/v1/patients?limit={limit}", "100"))
                .andRespond(withSuccess("{\"data\":[],\"hasMore\":false,\"nextCursor\":null}", MediaType.APPLICATION_JSON));

        client.getPatients(null, null, 100);

        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 404, 429, 500})
    @DisplayName("status de erro HTTP propaga como exceção (não é engolido pelo client)")
    void errorStatus_propagatesAsException(int status) {
        server.expect(requestToUriTemplate("https://darwin.test/v1/patients?limit={limit}", "10"))
                .andRespond(withStatus(HttpStatusCode.valueOf(status)));

        assertThatThrownBy(() -> client.getPatients(null, null, 10))
                .isInstanceOf(RestClientResponseException.class);
    }

    @Test
    @DisplayName("timeout/falha de conexão propaga como exceção")
    void timeout_propagatesAsException() {
        server.expect(requestToUriTemplate("https://darwin.test/v1/patients?limit={limit}", "10"))
                .andRespond(request -> {
                    throw new java.io.IOException("timeout simulado");
                });

        assertThatThrownBy(() -> client.getPatients(null, null, 10))
                .isInstanceOf(ResourceAccessException.class);
    }

    @Test
    @DisplayName("getAppointments usa /v1/appointments com os mesmos parâmetros")
    void getAppointments_usesCorrectPath() {
        server.expect(requestToUriTemplate("https://darwin.test/v1/appointments?limit={limit}", "25"))
                .andRespond(withSuccess("{\"data\":[],\"hasMore\":false,\"nextCursor\":null}", MediaType.APPLICATION_JSON));

        client.getAppointments(null, null, 25);

        server.verify();
    }

    @Test
    @DisplayName("getPatientNotes usa /v1/patients/{id}/notes")
    void getPatientNotes_usesCorrectPath() {
        server.expect(requestToUriTemplate(
                        "https://darwin.test/v1/patients/{patientId}/notes?limit={limit}", "pac-123", "10"))
                .andRespond(withSuccess("{\"data\":[],\"hasMore\":false,\"nextCursor\":null}", MediaType.APPLICATION_JSON));

        client.getPatientNotes("pac-123", null, 10);

        server.verify();
    }

    @Test
    @DisplayName("resposta vazia (sem próxima página) é interpretada corretamente")
    void emptyResponse_isHandled() {
        server.expect(requestToUriTemplate("https://darwin.test/v1/patients?limit={limit}", "10"))
                .andRespond(withSuccess("{\"data\":[],\"hasMore\":false,\"nextCursor\":null}", MediaType.APPLICATION_JSON));

        DarwinPageResponse<DarwinPatientDTO> response = client.getPatients(null, null, 10);

        assertThat(response.data()).isEmpty();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    @DisplayName("paginação: has_more=true e next_cursor presente são interpretados")
    void pagination_hasMoreAndNextCursor_areParsed() {
        // Contrato real do Darwin: chaves snake_case (has_more / next_cursor) — ver DarwinPageResponse.
        server.expect(requestToUriTemplate("https://darwin.test/v1/patients?limit={limit}", "2"))
                .andRespond(withSuccess(
                        "{\"data\":[{},{}],\"has_more\":true,\"next_cursor\":\"cur-2\"}", MediaType.APPLICATION_JSON));

        DarwinPageResponse<DarwinPatientDTO> response = client.getPatients(null, null, 2);

        assertThat(response.data()).hasSize(2);
        assertThat(response.hasMore()).isTrue();
        assertThat(response.nextCursor()).isEqualTo("cur-2");
    }

    @Test
    @DisplayName("JSON inválido no corpo propaga como exceção (não é engolido pelo client)")
    void invalidJson_propagatesAsException() {
        server.expect(requestToUriTemplate("https://darwin.test/v1/patients?limit={limit}", "10"))
                .andRespond(withSuccess("isto-nao-e-json{{{", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getPatients(null, null, 10))
                .isInstanceOf(org.springframework.web.client.RestClientException.class);
    }

    @Test
    @DisplayName("contrato read-only: o client não expõe nenhum método de escrita (create/update/delete/save/post)")
    void client_hasNoWriteMethods() {
        java.util.List<String> metodosPublicos = java.util.Arrays.stream(DarwinClient.class.getDeclaredMethods())
                .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers()))
                .map(java.lang.reflect.Method::getName)
                .toList();

        assertThat(metodosPublicos)
                .containsExactlyInAnyOrder("getPatients", "getAppointments", "getPatientNotes");
        assertThat(metodosPublicos).noneMatch(nome -> {
            String n = nome.toLowerCase(java.util.Locale.ROOT);
            return n.startsWith("create") || n.startsWith("update") || n.startsWith("delete")
                    || n.startsWith("save") || n.startsWith("post") || n.startsWith("put")
                    || n.startsWith("patch") || n.startsWith("remove") || n.startsWith("write");
        });
    }
}
