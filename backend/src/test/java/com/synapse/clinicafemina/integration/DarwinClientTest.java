package com.synapse.clinicafemina.integration;

import com.synapse.clinicafemina.dto.darwin.DarwinAvailableTimetablesResponse;
import com.synapse.clinicafemina.dto.darwin.DarwinInsuranceListResponse;
import com.synapse.clinicafemina.dto.darwin.DarwinLocationRef;
import com.synapse.clinicafemina.dto.darwin.DarwinPatientRecordDTO;
import com.synapse.clinicafemina.dto.darwin.DarwinPatientScheduleResponse;
import com.synapse.clinicafemina.dto.darwin.DarwinProcedureListResponse;
import com.synapse.clinicafemina.dto.darwin.DarwinProfessionalRef;
import com.synapse.clinicafemina.dto.darwin.DarwinProfessionalTimetableDTO;
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
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * DarwinClient — contrato read-only baseado exclusivamente nos 8 endpoints GET
 * documentados na coleção Postman oficial "API de integração Darwin" v1.0.9.
 * Nenhum endpoint de escrita (create/update/archive/delete) é exercitado ou implementado.
 */
@DisplayName("DarwinClient — contrato read-only da API oficial Darwin v1.0.9")
class DarwinClientTest {

    private MockRestServiceServer server;
    private DarwinClient client;

    @BeforeEach
    void setUp() throws Exception {
        client = new DarwinClient("https://darwin.test", "secret-darwin-token");
        RestClient.Builder builder = RestClient.builder().baseUrl("https://darwin.test")
                .defaultHeader(AUTHORIZATION, "Bearer secret-darwin-token")
                .defaultHeader(ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient mockedRestClient = builder.build();

        Field restClientField = DarwinClient.class.getDeclaredField("restClient");
        restClientField.setAccessible(true);
        restClientField.set(client, mockedRestClient);
    }

    @Test
    @DisplayName("listarHorariosDisponiveis envia Bearer, Accept e date; parseia horários disponíveis")
    void listarHorariosDisponiveis_enviaAuthEDate_parseiaResposta() {
        server.expect(requestToUriTemplate(
                        "https://darwin.test/api/timetables/list/available?date={date}", "2026-07-20"))
                .andExpect(method(GET))
                .andExpect(header(AUTHORIZATION, "Bearer secret-darwin-token"))
                .andExpect(header(ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess("""
                        {
                          "minimumOnlineScheduleAdvanceTime": "PT1H",
                          "professionalsAvailableTimes": [
                            {
                              "professionalId": "prof-1",
                              "professionalName": "Dra. Fulana",
                              "timetables": [
                                {
                                  "timetableId": "tt-1",
                                  "locationId": "loc-1",
                                  "locationName": "Unidade Centro",
                                  "locationData": null,
                                  "procedures": [{"id":"proc-1","name":"Consulta","timeSpent":30}],
                                  "insurances": [{"id":"ins-1","name":"Particular"}],
                                  "date": "2026-07-20T00:00:00.000Z",
                                  "availableTimes": [{"startTime":"09:00","endTime":"09:30"}]
                                }
                              ]
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        DarwinAvailableTimetablesResponse response =
                client.listarHorariosDisponiveis(LocalDate.of(2026, 7, 20), null);

        assertThat(response.professionalsAvailableTimes()).hasSize(1);
        assertThat(response.professionalsAvailableTimes().get(0).timetables()).hasSize(1);
        assertThat(response.professionalsAvailableTimes().get(0).timetables().get(0).availableTimes())
                .hasSize(1);
        server.verify();
    }

    @Test
    @DisplayName("listarHorariosDisponiveis com professionalIds inclui o parâmetro na URI")
    void listarHorariosDisponiveis_comProfessionalIds_incluiParametro() {
        server.expect(requestToUriTemplate(
                        "https://darwin.test/api/timetables/list/available?date={date}&professionalIds={id}",
                        "2026-07-20", "prof-1"))
                .andRespond(withSuccess(
                        "{\"minimumOnlineScheduleAdvanceTime\":null,\"professionalsAvailableTimes\":[]}",
                        MediaType.APPLICATION_JSON));

        client.listarHorariosDisponiveis(LocalDate.of(2026, 7, 20), List.of("prof-1"));

        server.verify();
    }

    @Test
    @DisplayName("listarGradesDoProfissional envia filtros opcionais e parseia grade (snake_case)")
    void listarGradesDoProfissional_enviaFiltros_parseiaGrade() {
        server.expect(requestToUriTemplate(
                        "https://darwin.test/api/timetables/list/professional?professionalId={pid}"
                                + "&locationId={lid}&status={status}&weekday={weekday}"
                                + "&isOnlineAvailable={online}&startDate={startDate}",
                        "prof-1", "loc-1", "ativa", "monday", "true", "2026-07-01"))
                .andRespond(withSuccess("""
                        [
                          {
                            "id": "tt-1",
                            "monday": true, "tuesday": false, "wednesday": false, "thursday": false,
                            "friday": false, "saturday": false, "sunday": false,
                            "start_time": "08:00", "end_time": "12:00", "interval_time": "00:30",
                            "start_datetime": "2026-07-01T00:00:00.000Z",
                            "end_datetime": null,
                            "location_id": "loc-1",
                            "isOnlineAvailable": true,
                            "locations": {"location_name": "Unidade Centro"},
                            "timetableProcedures": [{"procedures": {"id":"proc-1","name":"Consulta"}}],
                            "timetableInsurances": [{"insurances": {"id":"ins-1","name":"Particular"}}],
                            "timetableOnlineProcedures": [],
                            "timetableOnlineInsurances": []
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        List<DarwinProfessionalTimetableDTO> grades = client.listarGradesDoProfissional(
                "prof-1", "loc-1", "ativa", "monday", true, LocalDate.of(2026, 7, 1));

        assertThat(grades).hasSize(1);
        assertThat(grades.get(0).startTime()).isEqualTo("08:00");
        assertThat(grades.get(0).locations().locationName()).isEqualTo("Unidade Centro");
        assertThat(grades.get(0).timetableProcedures().get(0).procedures().name()).isEqualTo("Consulta");
        server.verify();
    }

    @Test
    @DisplayName("listarProfissionaisDoLocal parseia lista de profissionais")
    void listarProfissionaisDoLocal_parseiaLista() {
        server.expect(requestToUriTemplate("https://darwin.test/api/professionals/list/locations"))
                .andRespond(withSuccess(
                        "[{\"professionalId\":\"prof-1\",\"professionalName\":\"Dra. Fulana\"}]",
                        MediaType.APPLICATION_JSON));

        List<DarwinProfessionalRef> profissionais = client.listarProfissionaisDoLocal();

        assertThat(profissionais).hasSize(1);
        assertThat(profissionais.get(0).professionalId()).isEqualTo("prof-1");
        server.verify();
    }

    @Test
    @DisplayName("listarAgendamentosPorCpf envia cpf e filtros; parseia agendamentos do paciente")
    void listarAgendamentosPorCpf_enviaCpfEFiltros_parseiaResposta() {
        server.expect(requestToUriTemplate(
                        "https://darwin.test/api/schedules/list/patient?cpf={cpf}"
                                + "&startDate={startDate}&endDate={endDate}&status={status}",
                        "000.000.000-00", "2026-07-01", "2026-07-31", "Marcado"))
                .andRespond(withSuccess("""
                        {
                          "patient": {"cpf": "000.000.000-00", "name": "Paciente Teste"},
                          "tags": [{"locationId":"loc-1","description":"VIP","locationName":"Unidade Centro"}],
                          "schedules": [
                            {
                              "date": "2026-07-20T00:00:00.000Z", "time": "09:00",
                              "statusId": "st-1", "scheduleId": "sch-1", "statusName": "Marcado",
                              "locationId": "loc-1", "professionalId": "prof-1",
                              "professionalName": "Dra. Fulana", "endTime": "09:30",
                              "timetableId": "tt-1", "locationName": "Unidade Centro",
                              "schedule_procedures": [{"procedures":{"name":"Consulta"},"insurances":{"name":"Particular"}}],
                              "locationData": null
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        DarwinPatientScheduleResponse response = client.listarAgendamentosPorCpf(
                "000.000.000-00", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), "Marcado");

        assertThat(response.patient().cpf()).isEqualTo("000.000.000-00");
        assertThat(response.schedules()).hasSize(1);
        assertThat(response.schedules().get(0).scheduleProcedures().get(0).procedures().name())
                .isEqualTo("Consulta");
        server.verify();
    }

    @Test
    @DisplayName("buscarPacientePorCpf parseia ficha cadastral completa")
    void buscarPacientePorCpf_parseiaFicha() {
        server.expect(requestToUriTemplate(
                        "https://darwin.test/api/patients/find/cpf?cpf={cpf}", "000.000.000-00"))
                .andRespond(withSuccess("""
                        {
                          "cpf": "000.000.000-00", "name": "Paciente Teste", "rg": null, "cns": null,
                          "job": null, "email": "paciente@example.test", "gender": "F",
                          "skinColor": null, "socialName": null, "motherName": null, "fatherName": null,
                          "naturalness": null, "birthDate": "1990-01-01T00:00:00.000Z",
                          "phoneNumber": "11999998888", "extraPhones": [],
                          "address": {"cep":"00000-000","city":"São Paulo","state":"SP","number":10,
                            "address":"Rua Teste","complement":null,"neighborhood":"Centro"}
                        }
                        """, MediaType.APPLICATION_JSON));

        DarwinPatientRecordDTO paciente = client.buscarPacientePorCpf("000.000.000-00");

        assertThat(paciente.name()).isEqualTo("Paciente Teste");
        assertThat(paciente.address().city()).isEqualTo("São Paulo");
        server.verify();
    }

    @Test
    @DisplayName("buscarPacientePorCpf com CPF fora do escopo do token propaga 404")
    void buscarPacientePorCpf_cpfForaDoEscopo_propaga404() {
        server.expect(requestToUriTemplate(
                        "https://darwin.test/api/patients/find/cpf?cpf={cpf}", "111.111.111-11"))
                .andRespond(withStatus(HttpStatusCode.valueOf(404)));

        assertThatThrownBy(() -> client.buscarPacientePorCpf("111.111.111-11"))
                .isInstanceOf(RestClientResponseException.class);
    }

    @Test
    @DisplayName("listarProcedimentos envia paginação e parseia procedimentos")
    void listarProcedimentos_comPaginacao_parseiaLista() {
        server.expect(requestToUriTemplate(
                        "https://darwin.test/api/procedures/list/location?locationId={lid}"
                                + "&name={name}&page={page}&amount={amount}",
                        "loc-1", "Consulta", "1", "20"))
                .andRespond(withSuccess("""
                        {"procedures":[{"id":"proc-1","name":"Consulta"}],
                         "pagination":{"page":1,"amount":20,"totalItems":1,"totalPages":1}}
                        """, MediaType.APPLICATION_JSON));

        DarwinProcedureListResponse response =
                client.listarProcedimentos("loc-1", "Consulta", 1, 20);

        assertThat(response.procedures()).hasSize(1);
        assertThat(response.pagination().totalItems()).isEqualTo(1);
        server.verify();
    }

    @Test
    @DisplayName("listarLocaisDoProfissional parseia lista de locais")
    void listarLocaisDoProfissional_parseiaLista() {
        server.expect(requestToUriTemplate("https://darwin.test/api/locations/list/professional"))
                .andRespond(withSuccess(
                        "[{\"locationId\":\"loc-1\",\"locationName\":\"Unidade Centro\"}]",
                        MediaType.APPLICATION_JSON));

        List<DarwinLocationRef> locais = client.listarLocaisDoProfissional();

        assertThat(locais).hasSize(1);
        assertThat(locais.get(0).locationName()).isEqualTo("Unidade Centro");
        server.verify();
    }

    @Test
    @DisplayName("listarConvenios envia paginação e parseia convênios")
    void listarConvenios_comPaginacao_parseiaLista() {
        server.expect(requestToUriTemplate(
                        "https://darwin.test/api/insurances/list/location?locationId={lid}"
                                + "&name={name}&page={page}&amount={amount}",
                        "loc-1", "Particular", "1", "20"))
                .andRespond(withSuccess("""
                        {"insurances":[{"id":"ins-1","name":"Particular"}],
                         "pagination":{"page":1,"amount":20,"totalItems":1,"totalPages":1}}
                        """, MediaType.APPLICATION_JSON));

        DarwinInsuranceListResponse response =
                client.listarConvenios("loc-1", "Particular", 1, 20);

        assertThat(response.insurances()).hasSize(1);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 429, 500})
    @DisplayName("status de erro HTTP propaga como exceção (não é engolido pelo client)")
    void errorStatus_propagatesAsException(int status) {
        server.expect(requestToUriTemplate(
                        "https://darwin.test/api/timetables/list/available?date={date}", "2026-07-20"))
                .andRespond(withStatus(HttpStatusCode.valueOf(status)));

        assertThatThrownBy(() -> client.listarHorariosDisponiveis(LocalDate.of(2026, 7, 20), null))
                .isInstanceOf(RestClientResponseException.class);
    }

    @Test
    @DisplayName("timeout/falha de conexão propaga como exceção")
    void timeout_propagatesAsException() {
        server.expect(requestToUriTemplate(
                        "https://darwin.test/api/timetables/list/available?date={date}", "2026-07-20"))
                .andRespond(request -> {
                    throw new java.io.IOException("timeout simulado");
                });

        assertThatThrownBy(() -> client.listarHorariosDisponiveis(LocalDate.of(2026, 7, 20), null))
                .isInstanceOf(ResourceAccessException.class);
    }

    @Test
    @DisplayName("resposta vazia (sem profissionais/procedimentos) é interpretada corretamente")
    void respostaVazia_eInterpretadaCorretamente() {
        server.expect(requestToUriTemplate(
                        "https://darwin.test/api/procedures/list/location?locationId={lid}"
                                + "&name={name}&page={page}&amount={amount}",
                        "loc-1", "Nada", "1", "20"))
                .andRespond(withSuccess(
                        "{\"procedures\":[],\"pagination\":{\"page\":1,\"amount\":20,\"totalItems\":0,\"totalPages\":0}}",
                        MediaType.APPLICATION_JSON));

        DarwinProcedureListResponse response = client.listarProcedimentos("loc-1", "Nada", 1, 20);

        assertThat(response.procedures()).isEmpty();
        assertThat(response.pagination().totalItems()).isZero();
    }

    @Test
    @DisplayName("JSON inválido no corpo propaga como exceção (não é engolido pelo client)")
    void invalidJson_propagatesAsException() {
        server.expect(requestToUriTemplate(
                        "https://darwin.test/api/timetables/list/available?date={date}", "2026-07-20"))
                .andRespond(withSuccess("isto-nao-e-json{{{", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.listarHorariosDisponiveis(LocalDate.of(2026, 7, 20), null))
                .isInstanceOf(RestClientException.class);
    }

    @Test
    @DisplayName("contrato read-only: nenhum método de escrita (create/update/archive/delete) é exposto")
    void client_hasNoWriteMethods() {
        List<String> metodosPublicos = Arrays.stream(DarwinClient.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .map(Method::getName)
                .toList();

        assertThat(metodosPublicos).containsExactlyInAnyOrder(
                "listarHorariosDisponiveis",
                "listarGradesDoProfissional",
                "listarProfissionaisDoLocal",
                "listarAgendamentosPorCpf",
                "buscarPacientePorCpf",
                "listarProcedimentos",
                "listarLocaisDoProfissional",
                "listarConvenios");
        assertThat(metodosPublicos).noneMatch(nome -> {
            String n = nome.toLowerCase(Locale.ROOT);
            return n.startsWith("create") || n.startsWith("update") || n.startsWith("archive")
                    || n.startsWith("delete") || n.startsWith("save") || n.startsWith("post")
                    || n.startsWith("put") || n.startsWith("patch") || n.startsWith("remove")
                    || n.startsWith("write") || n.startsWith("upload");
        });
    }
}
