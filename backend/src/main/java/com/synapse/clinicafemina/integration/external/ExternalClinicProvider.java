package com.synapse.clinicafemina.integration.external;

import java.time.OffsetDateTime;

public interface ExternalClinicProvider {

    ExternalProviderType getType();

    PageResult<ExternalPatientDTO> getPatients(OffsetDateTime updatedAfter, String cursor, int limit);

    PageResult<ExternalAppointmentDTO> getAppointments(OffsetDateTime updatedAfter, String cursor, int limit);

    PageResult<ExternalClinicalNoteDTO> getPatientNotes(String externalPatientId, String cursor, int limit);
}
