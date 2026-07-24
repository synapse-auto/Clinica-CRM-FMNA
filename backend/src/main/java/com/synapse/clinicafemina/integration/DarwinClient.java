package com.synapse.clinicafemina.integration;

import com.synapse.clinicafemina.dto.darwin.DarwinAvailableTimetablesResponse;
import com.synapse.clinicafemina.dto.darwin.DarwinInsuranceListResponse;
import com.synapse.clinicafemina.dto.darwin.DarwinLocationRef;
import com.synapse.clinicafemina.dto.darwin.DarwinPatientRecordDTO;
import com.synapse.clinicafemina.dto.darwin.DarwinPatientScheduleResponse;
import com.synapse.clinicafemina.dto.darwin.DarwinProcedureListResponse;
import com.synapse.clinicafemina.dto.darwin.DarwinProfessionalRef;
import com.synapse.clinicafemina.dto.darwin.DarwinProfessionalTimetableDTO;
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
 * Cliente HTTP read-only para a API oficial de integração Darwin (v1.0.9).
 * Contrato estrito: somente os 8 endpoints GET documentados na coleção Postman oficial.
 * Nenhum método de escrita (create/update/archive/delete) é implementado nesta fase.
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
}
