package com.synapse.clinicafemina.integration.external;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.clinicafemina.dto.darwin.DarwinAppointmentDTO;
import com.synapse.clinicafemina.dto.darwin.DarwinNoteDTO;
import com.synapse.clinicafemina.dto.darwin.DarwinPageResponse;
import com.synapse.clinicafemina.dto.darwin.DarwinPatientDTO;
import com.synapse.clinicafemina.integration.DarwinClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class DarwinProvider implements ExternalClinicProvider {

    private static final int DEFAULT_APPOINTMENT_MINUTES = 30;

    private final DarwinClient darwinClient;
    private final ObjectMapper objectMapper;

    @Override
    public ExternalProviderType getType() {
        return ExternalProviderType.DARWIN;
    }

    @Override
    public PageResult<ExternalPatientDTO> getPatients(OffsetDateTime updatedAfter, String cursor, int limit) {
        DarwinPageResponse<DarwinPatientDTO> response = darwinClient.getPatients(updatedAfter, cursor, limit);
        return new PageResult<>(
                response.data().stream().map(this::toExternalPatient).toList(),
                response.hasMore(),
                response.nextCursor()
        );
    }

    @Override
    public PageResult<ExternalAppointmentDTO> getAppointments(OffsetDateTime updatedAfter, String cursor, int limit) {
        DarwinPageResponse<DarwinAppointmentDTO> response = darwinClient.getAppointments(updatedAfter, cursor, limit);
        return new PageResult<>(
                response.data().stream().map(this::toExternalAppointment).toList(),
                response.hasMore(),
                response.nextCursor()
        );
    }

    @Override
    public PageResult<ExternalClinicalNoteDTO> getPatientNotes(String externalPatientId, String cursor, int limit) {
        DarwinPageResponse<DarwinNoteDTO> response = darwinClient.getPatientNotes(externalPatientId, cursor, limit);
        return new PageResult<>(
                response.data().stream().map(this::toExternalNote).toList(),
                response.hasMore(),
                response.nextCursor()
        );
    }

    private ExternalPatientDTO toExternalPatient(DarwinPatientDTO dto) {
        return new ExternalPatientDTO(
                dto.id(),
                dto.fullName(),
                dto.documentNumber(),
                dto.email(),
                dto.phone(),
                dto.birthDate(),
                dto.updatedAt(),
                toMap(dto)
        );
    }

    private ExternalAppointmentDTO toExternalAppointment(DarwinAppointmentDTO dto) {
        OffsetDateTime endAt = dto.scheduledTime() != null
                ? dto.scheduledTime().plusMinutes(DEFAULT_APPOINTMENT_MINUTES)
                : null;
        return new ExternalAppointmentDTO(
                dto.id(),
                dto.patientId(),
                dto.scheduledTime(),
                endAt,
                "CONSULTA",
                null,
                dto.status(),
                null,
                null,
                null,
                toMap(dto)
        );
    }

    private ExternalClinicalNoteDTO toExternalNote(DarwinNoteDTO dto) {
        return new ExternalClinicalNoteDTO(
                dto.id(),
                dto.patientId(),
                dto.content(),
                dto.createdAt(),
                toMap(dto)
        );
    }

    private Map<String, Object> toMap(Object dto) {
        return objectMapper.convertValue(dto, new TypeReference<>() {});
    }
}
