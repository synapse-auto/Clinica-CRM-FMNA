package com.synapse.clinicafemina.integration.external;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Slf4j
@Component
public class MedwareProvider implements ExternalClinicProvider {

    private final String apiUrl;
    private final String apiToken;

    public MedwareProvider(
            @Value("${app.medware.api-url:}") String apiUrl,
            @Value("${app.medware.api-token:}") String apiToken) {
        this.apiUrl = apiUrl;
        this.apiToken = apiToken;
    }

    @Override
    public ExternalProviderType getType() {
        return ExternalProviderType.MEDWARE;
    }

    @Override
    public PageResult<ExternalPatientDTO> getPatients(OffsetDateTime updatedAfter, String cursor, int limit) {
        assertConfigured();
        throw skeletonException();
    }

    @Override
    public PageResult<ExternalAppointmentDTO> getAppointments(OffsetDateTime updatedAfter, String cursor, int limit) {
        assertConfigured();
        throw skeletonException();
    }

    @Override
    public PageResult<ExternalClinicalNoteDTO> getPatientNotes(String externalPatientId, String cursor, int limit) {
        assertConfigured();
        throw skeletonException();
    }

    private void assertConfigured() {
        if (apiUrl == null || apiUrl.isBlank() || apiToken == null || apiToken.isBlank()) {
            throw new IllegalStateException("MEDWARE_API_URL e MEDWARE_API_TOKEN devem ser configuradas para habilitar o provider Medware");
        }
    }

    private UnsupportedOperationException skeletonException() {
        return new UnsupportedOperationException(
                "MedwareProvider é um skeleton aguardando documentação real da API Medware");
    }
}
