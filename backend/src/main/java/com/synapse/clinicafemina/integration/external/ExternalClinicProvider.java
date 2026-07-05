package com.synapse.clinicafemina.integration.external;

import java.time.OffsetDateTime;
import java.time.LocalDate;

public interface ExternalClinicProvider {

    ExternalProviderType getType();

    PageResult<ExternalPatientDTO> getPatients(OffsetDateTime updatedAfter, String cursor, int limit);

    PageResult<ExternalAppointmentDTO> getAppointments(OffsetDateTime updatedAfter, String cursor, int limit);

    default PageResult<ExternalAppointmentDTO> getAppointments(
            OffsetDateTime updatedAfter,
            LocalDate dataInicio,
            LocalDate dataFim,
            String cursor,
            int limit
    ) {
        return getAppointments(updatedAfter, cursor, limit);
    }

    PageResult<ExternalClinicalNoteDTO> getPatientNotes(String externalPatientId, String cursor, int limit);
}
