package com.synapse.clinicafemina.integration.external;

import java.time.OffsetDateTime;
import java.time.LocalDate;

public interface ExternalClinicProvider {

    ExternalProviderType getType();

    /**
     * Indica se este provider suporta sincronização em massa (listagem incremental
     * de pacientes/agendamentos via {@link #getPatients} e {@link #getAppointments}).
     * Default {@code true} para preservar o comportamento de providers legados (Medware).
     */
    default boolean supportsBulkSync() {
        return true;
    }

    /**
     * Indica se este provider suporta consultas pontuais sob demanda
     * (ex.: Darwin — busca por CPF, horários disponíveis, catálogos).
     */
    default boolean supportsOnDemandQueries() {
        return false;
    }

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
