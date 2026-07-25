package com.synapse.clinicafemina.integration;

import com.synapse.clinicafemina.dto.darwin.DarwinAvailableTimetablesResponse;
import com.synapse.clinicafemina.dto.darwin.DarwinCreateFitInScheduleRequest;
import com.synapse.clinicafemina.dto.darwin.DarwinCreatePatientRequest;
import com.synapse.clinicafemina.dto.darwin.DarwinCreatePatientResponse;
import com.synapse.clinicafemina.dto.darwin.DarwinCreateScheduleRequest;
import com.synapse.clinicafemina.dto.darwin.DarwinInsuranceListResponse;
import com.synapse.clinicafemina.dto.darwin.DarwinLocationRef;
import com.synapse.clinicafemina.dto.darwin.DarwinPatientRecordDTO;
import com.synapse.clinicafemina.dto.darwin.DarwinPatientScheduleResponse;
import com.synapse.clinicafemina.dto.darwin.DarwinProcedureListResponse;
import com.synapse.clinicafemina.dto.darwin.DarwinProfessionalRef;
import com.synapse.clinicafemina.dto.darwin.DarwinProfessionalTimetableDTO;
import com.synapse.clinicafemina.dto.darwin.DarwinUpdatePatientRequest;
import com.synapse.clinicafemina.dto.darwin.DarwinUpdateScheduleRequest;
import com.synapse.clinicafemina.dto.darwin.DarwinWriteMessageResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Cliente HTTP para a API oficial de integração Darwin (v1.0.9). Contrato estrito:
 * somente os 8 endpoints GET de consulta e os 6 endpoints de escrita
 * (patients/create, patients/update, schedules/create, schedules/create/fitin,
 * schedules/update, schedules/delete) documentados na coleção Postman oficial.
 * Nenhuma escrita real contra a API Darwin foi validada nesta sessão — apenas
 * contrato (métodos/paths/headers) via testes com mock; ver roteiro de smoke test
 * separado antes de autorizar uso em produção.
 */
@Component
public class DarwinClient {

    private final RestClient restClient;

    public DarwinClient(
            @Value("${app.darwin.api-url}") String apiUrl,
            @Value("${app.darwin.api-token}") String apiToken) {
        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public DarwinAvailableTimetablesResponse listarHorariosDisponiveis(
            LocalDate date, List<String> professionalIds) {
        String uri = UriComponentsBuilder.fromPath("/api/timetables/list/available")
                .queryParam("date", date)
                .queryParamIfPresent("professionalIds",
                        Optional.ofNullable(professionalIds).filter(ids -> !ids.isEmpty()))
                .build().toUriString();
        return restClient.get().uri(uri).retrieve().body(DarwinAvailableTimetablesResponse.class);
    }

    public List<DarwinProfessionalTimetableDTO> listarGradesDoProfissional(
            String professionalId, String locationId, String status, String weekday,
            Boolean isOnlineAvailable, LocalDate startDate) {
        String uri = UriComponentsBuilder.fromPath("/api/timetables/list/professional")
                .queryParamIfPresent("professionalId", Optional.ofNullable(professionalId))
                .queryParamIfPresent("locationId", Optional.ofNullable(locationId))
                .queryParamIfPresent("status", Optional.ofNullable(status))
                .queryParamIfPresent("weekday", Optional.ofNullable(weekday))
                .queryParamIfPresent("isOnlineAvailable", Optional.ofNullable(isOnlineAvailable))
                .queryParamIfPresent("startDate", Optional.ofNullable(startDate))
                .build().toUriString();
        return restClient.get().uri(uri).retrieve().body(new ParameterizedTypeReference<>() {});
    }

    public List<DarwinProfessionalRef> listarProfissionaisDoLocal() {
        return restClient.get()
                .uri("/api/professionals/list/locations")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public DarwinPatientScheduleResponse listarAgendamentosPorCpf(
            String cpf, LocalDate startDate, LocalDate endDate, String status) {
        String uri = UriComponentsBuilder.fromPath("/api/schedules/list/patient")
                .queryParam("cpf", cpf)
                .queryParamIfPresent("startDate", Optional.ofNullable(startDate))
                .queryParamIfPresent("endDate", Optional.ofNullable(endDate))
                .queryParamIfPresent("status", Optional.ofNullable(status))
                .build().toUriString();
        return restClient.get().uri(uri).retrieve().body(DarwinPatientScheduleResponse.class);
    }

    public DarwinPatientRecordDTO buscarPacientePorCpf(String cpf) {
        String uri = UriComponentsBuilder.fromPath("/api/patients/find/cpf")
                .queryParam("cpf", cpf)
                .build().toUriString();
        return restClient.get().uri(uri).retrieve().body(DarwinPatientRecordDTO.class);
    }

    public DarwinProcedureListResponse listarProcedimentos(
            String locationId, String name, Integer page, Integer amount) {
        String uri = UriComponentsBuilder.fromPath("/api/procedures/list/location")
                .queryParamIfPresent("locationId", Optional.ofNullable(locationId))
                .queryParamIfPresent("name", Optional.ofNullable(name))
                .queryParamIfPresent("page", Optional.ofNullable(page))
                .queryParamIfPresent("amount", Optional.ofNullable(amount))
                .build().toUriString();
        return restClient.get().uri(uri).retrieve().body(DarwinProcedureListResponse.class);
    }

    public List<DarwinLocationRef> listarLocaisDoProfissional() {
        return restClient.get()
                .uri("/api/locations/list/professional")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public DarwinInsuranceListResponse listarConvenios(
            String locationId, String name, Integer page, Integer amount) {
        String uri = UriComponentsBuilder.fromPath("/api/insurances/list/location")
                .queryParamIfPresent("locationId", Optional.ofNullable(locationId))
                .queryParamIfPresent("name", Optional.ofNullable(name))
                .queryParamIfPresent("page", Optional.ofNullable(page))
                .queryParamIfPresent("amount", Optional.ofNullable(amount))
                .build().toUriString();
        return restClient.get().uri(uri).retrieve().body(DarwinInsuranceListResponse.class);
    }

    public DarwinCreatePatientResponse criarPaciente(DarwinCreatePatientRequest request) {
        return restClient.post()
                .uri("/api/patients/create")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(DarwinCreatePatientResponse.class);
    }

    public DarwinPatientRecordDTO atualizarPaciente(DarwinUpdatePatientRequest request) {
        return restClient.put()
                .uri("/api/patients/update")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(DarwinPatientRecordDTO.class);
    }

    public DarwinWriteMessageResponse criarAgendamento(DarwinCreateScheduleRequest request) {
        return restClient.post()
                .uri("/api/schedules/create")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(DarwinWriteMessageResponse.class);
    }

    public DarwinWriteMessageResponse criarAgendamentoEncaixe(DarwinCreateFitInScheduleRequest request) {
        return restClient.post()
                .uri("/api/schedules/create/fitin")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(DarwinWriteMessageResponse.class);
    }

    public DarwinWriteMessageResponse atualizarAgendamento(DarwinUpdateScheduleRequest request) {
        return restClient.put()
                .uri("/api/schedules/update")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(DarwinWriteMessageResponse.class);
    }

    public DarwinWriteMessageResponse excluirAgendamento(String scheduleId) {
        String uri = UriComponentsBuilder.fromPath("/api/schedules/delete")
                .queryParam("scheduleId", scheduleId)
                .build().toUriString();
        return restClient.delete().uri(uri).retrieve().body(DarwinWriteMessageResponse.class);
    }
}
