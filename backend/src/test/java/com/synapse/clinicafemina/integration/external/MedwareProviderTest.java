package com.synapse.clinicafemina.integration.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MedwareProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-06-15T12:00:00Z"), ZoneId.of("America/Sao_Paulo"));

    @Test
    void should_fail_safely_when_medware_credentials_are_missing() {
        MedwareProvider provider = createProvider(RestClient.builder(), "", "", "", false);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> provider.getPatients(null, null, 50));

        assertTrue(ex.getMessage().contains("MEDWARE_API_URL"));
        assertTrue(ex.getMessage().contains("MEDWARE_USERNAME"));
        assertTrue(ex.getMessage().contains("MEDWARE_PASSWORD"));
    }

    @Test
    void should_login_and_read_patients_with_bearer_token_without_real_network_call() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MedwareProvider provider = createProvider(builder, "https://medware.example/api", "usuario", "senha", false);

        server.expect(once(), requestTo("https://medware.example/api/Acesso/login"))
                .andExpect(method(POST))
                .andExpect(content().json("""
                        {"usuario":"usuario","senha":"senha"}
                        """, true))
                .andRespond(withSuccess("""
                        {"token":"jwt-token","refreshToken":"refresh-token"}
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://medware.example/api/Medware/Paciente/Listar"))
                .andExpect(method(GET))
                .andExpect(header("Authorization", "Bearer jwt-token"))
                .andRespond(withSuccess("""
                        [
                          {
                            "codPaciente": 1023,
                            "nome": "Maria Oliveira",
                            "cpf": "98765432100",
                            "email": "maria@example.test",
                            "numeroCelularddd": "61",
                            "numeroCelular": "998877665"
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        PageResult<ExternalPatientDTO> page = provider.getPatients(null, null, 50);

        assertEquals(1, page.data().size());
        assertEquals("1023", page.data().getFirst().externalId());
        assertEquals("Maria Oliveira", page.data().getFirst().fullName());
        assertEquals("61998877665", page.data().getFirst().phone());
        server.verify();
    }

    @Test
    void should_filter_invalid_patient_and_preserve_null_optional_payload_fields() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MedwareProvider provider = createProvider(builder, "https://medware.example/api", "usuario", "senha", false);

        server.expect(once(), requestTo("https://medware.example/api/Acesso/login"))
                .andExpect(method(POST))
                .andRespond(withSuccess("{\"token\":\"jwt-token\"}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://medware.example/api/Medware/Paciente/Listar"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        [
                          {"nome":"Sem identificador"},
                          {
                            "codPaciente":1023,
                            "nome":null,
                            "cpf":null,
                            "email":null,
                            "numeroCelular":null,
                            "observacao":null
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        PageResult<ExternalPatientDTO> page = provider.getPatients(null, null, 50);

        assertEquals(1, page.data().size());
        ExternalPatientDTO patient = page.data().getFirst();
        assertEquals("1023", patient.externalId());
        assertEquals(null, patient.fullName());
        assertEquals(null, patient.documentNumber());
        assertEquals(null, patient.email());
        assertEquals(null, patient.phone());
        assertTrue(patient.payload().containsKey("observacao"));
        assertEquals(null, patient.payload().get("observacao"));
        server.verify();
    }

    @Test
    void should_reject_html_login_response_with_clear_medware_url_message() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MedwareProvider provider = createProvider(builder, "https://medware.example", "usuario", "senha", false);

        server.expect(once(), requestTo("https://medware.example/Acesso/login"))
                .andExpect(method(POST))
                .andRespond(withSuccess("<html>login web</html>", MediaType.TEXT_HTML));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> provider.getPatients(null, null, 50));

        assertEquals(
                "MEDWARE_API_URL invalida ou endpoint nao retornou JSON. Verifique se a URL termina com /api.",
                ex.getMessage()
        );
        assertTrue(!ex.getMessage().contains("senha"));
        server.verify();
    }

    @Test
    void should_login_with_token_inside_retorno_object() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MedwareProvider provider = createProvider(builder, "https://medware.example/api", "usuario", "senha", false);

        server.expect(once(), requestTo("https://medware.example/api/Acesso/login"))
                .andExpect(method(POST))
                .andRespond(withSuccess("""
                        {"retorno":{"token":"jwt-token","refreshToken":"refresh-token"}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://medware.example/api/Medware/Paciente/Listar"))
                .andExpect(method(GET))
                .andExpect(header("Authorization", "Bearer jwt-token"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        PageResult<ExternalPatientDTO> page = provider.getPatients(null, null, 50);

        assertTrue(page.data().isEmpty());
        server.verify();
    }

    @Test
    void should_login_with_access_token_inside_retorno_object() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MedwareProvider provider = createProvider(builder, "https://medware.example/api", "usuario", "senha", false);

        server.expect(once(), requestTo("https://medware.example/api/Acesso/login"))
                .andExpect(method(POST))
                .andRespond(withSuccess("""
                        {"retorno":{"accessToken":"jwt-token"}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://medware.example/api/Medware/Paciente/Listar"))
                .andExpect(method(GET))
                .andExpect(header("Authorization", "Bearer jwt-token"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        PageResult<ExternalPatientDTO> page = provider.getPatients(null, null, 50);

        assertTrue(page.data().isEmpty());
        server.verify();
    }

    @Test
    void should_use_legacy_auth_contract_only_when_explicitly_configured() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MedwareProvider provider = createProvider(
                builder, "https://medware.example/api", "usuario", "senha", true,
                "IDENTIFICACAO_HASH", 30, 60
        );

        server.expect(once(), requestTo("https://medware.example/api/Acesso/login"))
                .andExpect(method(POST))
                .andExpect(content().json("""
                        {"identificacao":"usuario","senha":"senha","isHash":true}
                        """, true))
                .andRespond(withSuccess("{\"token\":\"jwt-token\"}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://medware.example/api/Medware/Paciente/Listar"))
                .andExpect(method(GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertTrue(provider.getPatients(null, null, 50).data().isEmpty());
        server.verify();
    }

    @Test
    void should_use_identification_auth_contract_without_is_hash_when_configured() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MedwareProvider provider = createProvider(
                builder, "https://medware.example/api", "usuario", "senha", true,
                "IDENTIFICACAO", 30, 60
        );

        server.expect(once(), requestTo("https://medware.example/api/Acesso/login"))
                .andExpect(method(POST))
                .andExpect(content().json("""
                        {"identificacao":"usuario","senha":"senha"}
                        """, true))
                .andRespond(withSuccess("{\"token\":\"jwt-token\"}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://medware.example/api/Medware/Paciente/Listar"))
                .andExpect(method(GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertTrue(provider.getPatients(null, null, 50).data().isEmpty());
        server.verify();
    }

    @Test
    void should_normalize_identification_mode_and_ignore_false_password_is_hash() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MedwareProvider provider = createProvider(
                builder, "https://medware.example/api", "usuario", "senha", false,
                "identificacao", 30, 60
        );

        server.expect(once(), requestTo("https://medware.example/api/Acesso/login"))
                .andExpect(method(POST))
                .andExpect(content().json("""
                        {"identificacao":"usuario","senha":"senha"}
                        """, true))
                .andRespond(withSuccess("{\"token\":\"jwt-token\"}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://medware.example/api/Medware/Paciente/Listar"))
                .andExpect(method(GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertTrue(provider.getPatients(null, null, 50).data().isEmpty());
        server.verify();
    }

    @Test
    void should_reject_invalid_auth_mode_with_clear_message() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> createProvider(
                        RestClient.builder(),
                        "https://medware.example/api",
                        "usuario",
                        "senha-secreta",
                        false,
                        "desconhecido",
                        30,
                        60
                )
        );

        assertEquals(
                "MEDWARE_AUTH_MODE deve ser USUARIO, IDENTIFICACAO ou IDENTIFICACAO_HASH",
                ex.getMessage()
        );
        assertTrue(!ex.getMessage().contains("senha-secreta"));
    }

    @Test
    void should_reauthenticate_once_after_unauthorized_read_request() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        MedwareProvider provider = createProvider(builder, "https://medware.example/api", "usuario", "senha", false);

        server.expect(once(), requestTo("https://medware.example/api/Acesso/login"))
                .andRespond(withSuccess("{\"token\":\"jwt-old\"}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://medware.example/api/Medware/Paciente/Listar"))
                .andExpect(header("Authorization", "Bearer jwt-old"))
                .andRespond(withStatus(UNAUTHORIZED));
        server.expect(once(), requestTo("https://medware.example/api/Acesso/login"))
                .andRespond(withSuccess("{\"token\":\"jwt-new\"}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://medware.example/api/Medware/Paciente/Listar"))
                .andExpect(header("Authorization", "Bearer jwt-new"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertTrue(provider.getPatients(null, null, 50).data().isEmpty());
        server.verify();
    }

    @Test
    void should_reject_login_json_without_valid_token_with_clear_credentials_message() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MedwareProvider provider = createProvider(builder, "https://medware.example/api", "usuario", "senha-secreta", false);

        server.expect(once(), requestTo("https://medware.example/api/Acesso/login"))
                .andExpect(method(POST))
                .andRespond(withSuccess("{\"retorno\":\"Usuario invalido\"}", MediaType.APPLICATION_JSON));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> provider.getPatients(null, null, 50));

        assertEquals("Credenciais Medware invalidas ou token ausente.", ex.getMessage());
        assertTrue(!ex.getMessage().contains("senha-secreta"));
        assertTrue(!ex.getMessage().contains("Usuario invalido"));
        server.verify();
    }

    @Test
    void should_read_appointments_with_date_window_and_catalog_enrichment() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        MedwareProvider provider = createProvider(builder, "https://medware.example/api", "usuario", "senha", false);

        server.expect(once(), requestTo("https://medware.example/api/Acesso/login"))
                .andExpect(method(POST))
                .andRespond(withSuccess("{\"token\":\"jwt-token\"}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://medware.example/api/Medware/ProcedPlanoOp/Listar?dataInicio=15/06/2026&dataFim=14/08/2026"))
                .andExpect(method(GET))
                .andExpect(header("Authorization", "Bearer jwt-token"))
                .andRespond(withSuccess("""
                        [{"codProcedimento":15,"descricaoProcedimento":"Ultrassom","duracao":30,"consulta":false}]
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://medware.example/api/Medware/Medico/Listar"))
                .andExpect(method(GET))
                .andExpect(header("Authorization", "Bearer jwt-token"))
                .andRespond(withSuccess("""
                        [{"codMedico":7,"nome":"Dra. Ana"}]
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://medware.example/api/Medware/Agendamento/Listar?dataInicio=15/06/2026&dataFim=14/08/2026"))
                .andExpect(method(GET))
                .andExpect(header("Authorization", "Bearer jwt-token"))
                .andRespond(withSuccess("""
                        [{"codAgendamento":555,"codPaciente":1023,"codMedico":7,"codProcedimento":15,"dataHoraAgendada":"2026-06-15T10:00:00"}]
                        """, MediaType.APPLICATION_JSON));

        PageResult<ExternalAppointmentDTO> page = provider.getAppointments(
                OffsetDateTime.parse("2026-06-15T08:00:00-03:00"), null, 50);

        assertEquals(1, page.data().size());
        assertEquals("555", page.data().getFirst().externalId());
        assertEquals("1023", page.data().getFirst().externalPatientId());
        assertEquals("Ultrassom", page.data().getFirst().serviceName());
        assertEquals("EXAME", page.data().getFirst().type());
        server.verify();
    }

    @Test
    void should_reject_html_appointment_response_with_clear_medware_url_message() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        MedwareProvider provider = createProvider(builder, "https://medware.example/api", "usuario", "senha", false);

        server.expect(once(), requestTo("https://medware.example/api/Acesso/login"))
                .andExpect(method(POST))
                .andRespond(withSuccess("{\"token\":\"jwt-token\"}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://medware.example/api/Medware/ProcedPlanoOp/Listar?dataInicio=15/06/2026&dataFim=14/08/2026"))
                .andExpect(method(GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://medware.example/api/Medware/Medico/Listar"))
                .andExpect(method(GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://medware.example/api/Medware/Agendamento/Listar?dataInicio=15/06/2026&dataFim=14/08/2026"))
                .andExpect(method(GET))
                .andRespond(withSuccess("<html>pagina web</html>", MediaType.TEXT_HTML));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> provider.getAppointments(OffsetDateTime.parse("2026-06-15T08:00:00-03:00"), null, 50));

        assertEquals(
                "MEDWARE_API_URL invalida ou endpoint nao retornou JSON. Verifique se a URL termina com /api.",
                ex.getMessage()
        );
        server.verify();
    }

    @Test
    void should_use_wider_default_date_window_when_no_manual_period_is_informed() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        MedwareProvider provider = createProvider(
                builder,
                "https://medware.example/api",
                "usuario",
                "senha",
                false,
                90,
                90
        );

        server.expect(once(), requestTo("https://medware.example/api/Acesso/login"))
                .andExpect(method(POST))
                .andRespond(withSuccess("{\"token\":\"jwt-token\"}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://medware.example/api/Medware/ProcedPlanoOp/Listar?dataInicio=17/03/2026&dataFim=13/09/2026"))
                .andExpect(method(GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://medware.example/api/Medware/Medico/Listar"))
                .andExpect(method(GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://medware.example/api/Medware/Agendamento/Listar?dataInicio=17/03/2026&dataFim=13/09/2026"))
                .andExpect(method(GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        PageResult<ExternalAppointmentDTO> page = provider.getAppointments(null, null, 50);

        assertTrue(page.data().isEmpty());
        server.verify();
    }

    @Test
    void should_read_appointments_with_explicit_date_window_in_medware_format() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        MedwareProvider provider = createProvider(builder, "https://medware.example/api", "usuario", "senha", false);

        server.expect(once(), requestTo("https://medware.example/api/Acesso/login"))
                .andExpect(method(POST))
                .andRespond(withSuccess("{\"token\":\"jwt-token\"}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://medware.example/api/Medware/ProcedPlanoOp/Listar?dataInicio=01/07/2026&dataFim=03/07/2026"))
                .andExpect(method(GET))
                .andExpect(header("Authorization", "Bearer jwt-token"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://medware.example/api/Medware/Medico/Listar"))
                .andExpect(method(GET))
                .andExpect(header("Authorization", "Bearer jwt-token"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://medware.example/api/Medware/Agendamento/Listar?dataInicio=01/07/2026&dataFim=03/07/2026"))
                .andExpect(method(GET))
                .andExpect(header("Authorization", "Bearer jwt-token"))
                .andRespond(withSuccess("""
                        [{"codAgendamento":555,"codPaciente":1023,"dataHoraAgenda":"03/07/2026 17:30"}]
                        """, MediaType.APPLICATION_JSON));

        PageResult<ExternalAppointmentDTO> page = provider.getAppointments(
                null,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 3),
                null,
                50
        );

        assertEquals(1, page.data().size());
        assertEquals("555", page.data().getFirst().externalId());
        server.verify();
    }

    @Test
    void should_translate_medware_http_errors_without_sensitive_values() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MedwareProvider provider = createProvider(builder, "https://medware.example/api", "usuario", "senha-secreta", false);

        server.expect(once(), requestTo("https://medware.example/api/Acesso/login"))
                .andRespond(withSuccess("{\"token\":\"jwt-token\"}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://medware.example/api/Medware/Paciente/Listar"))
                .andRespond(withStatus(SERVICE_UNAVAILABLE).body("fora do ar"));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> provider.getPatients(null, null, 50));

        assertTrue(ex.getMessage().contains("503"));
        assertTrue(ex.getMessage().contains("/Medware/Paciente/Listar"));
        assertTrue(!ex.getMessage().contains("senha-secreta"));
        server.verify();
    }

    private MedwareProvider createProvider(RestClient.Builder builder, String apiUrl, String username,
                                           String password, boolean passwordIsHash) {
        return createProvider(builder, apiUrl, username, password, passwordIsHash, 30, 60);
    }

    private MedwareProvider createProvider(RestClient.Builder builder, String apiUrl, String username,
                                           String password, boolean passwordIsHash,
                                           int defaultStartDaysBack, int defaultEndDaysForward) {
        return createProvider(builder, apiUrl, username, password, passwordIsHash, "USUARIO",
                defaultStartDaysBack, defaultEndDaysForward);
    }

    private MedwareProvider createProvider(RestClient.Builder builder, String apiUrl, String username,
                                           String password, boolean passwordIsHash, String authMode,
                                           int defaultStartDaysBack, int defaultEndDaysForward) {
        return new MedwareProvider(
                builder,
                objectMapper,
                new MedwareApiMapper(objectMapper),
                fixedClock,
                apiUrl,
                username,
                password,
                passwordIsHash,
                authMode,
                300,
                5,
                defaultStartDaysBack,
                defaultEndDaysForward
        );
    }
}
