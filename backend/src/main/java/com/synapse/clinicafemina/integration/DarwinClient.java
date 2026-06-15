package com.synapse.clinicafemina.integration;

import com.synapse.clinicafemina.dto.darwin.DarwinAppointmentDTO;
import com.synapse.clinicafemina.dto.darwin.DarwinNoteDTO;
import com.synapse.clinicafemina.dto.darwin.DarwinPageResponse;
import com.synapse.clinicafemina.dto.darwin.DarwinPatientDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Cliente HTTP Read-Only para a API do ERP Darwin.
 * Contrato estrito: SÓ MÉTODOS GET. Nenhuma escrita é permitida.
 */
@Slf4j
@Component
public class DarwinClient {

    private final RestClient restClient;
    private final String apiUrl;
    private final String apiToken;

    public DarwinClient(
            @Value("${app.darwin.api-url}") String apiUrl,
            @Value("${app.darwin.api-token}") String apiToken) {
        this.apiUrl = apiUrl;
        this.apiToken = apiToken;
        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                .build();
    }

    public DarwinPageResponse<DarwinPatientDTO> getPatients(OffsetDateTime updatedAfter, String cursor, int limit) {
        String uri = UriComponentsBuilder.fromPath("/v1/patients")
                .queryParamIfPresent("updated_after", java.util.Optional.ofNullable(updatedAfter).map(dt -> dt.withOffsetSameInstant(ZoneOffset.UTC).toString()))
                .queryParamIfPresent("cursor", java.util.Optional.ofNullable(cursor))
                .queryParam("limit", limit)
                .build().toUriString();

        log.debug("GET Darwin patients: {}", uri);
        return restClient.get()
                .uri(uri)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public DarwinPageResponse<DarwinAppointmentDTO> getAppointments(OffsetDateTime updatedAfter, String cursor, int limit) {
        String uri = UriComponentsBuilder.fromPath("/v1/appointments")
                .queryParamIfPresent("updated_after", java.util.Optional.ofNullable(updatedAfter).map(dt -> dt.withOffsetSameInstant(ZoneOffset.UTC).toString()))
                .queryParamIfPresent("cursor", java.util.Optional.ofNullable(cursor))
                .queryParam("limit", limit)
                .build().toUriString();

        log.debug("GET Darwin appointments: {}", uri);
        return restClient.get()
                .uri(uri)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public DarwinPageResponse<DarwinNoteDTO> getPatientNotes(String patientId, String cursor, int limit) {
        String uri = UriComponentsBuilder.fromPath("/v1/patients/{patientId}/notes")
                .queryParamIfPresent("cursor", java.util.Optional.ofNullable(cursor))
                .queryParam("limit", limit)
                .buildAndExpand(patientId).toUriString();

        log.debug("GET Darwin notes");
        return restClient.get()
                .uri(uri)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }
}
