package com.synapse.clinicafemina.dto.darwin;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Corpo de resposta de GET /api/schedules/list/patient (coleção Postman Darwin v1.0.9).
 */
public record DarwinPatientScheduleResponse(
        Patient patient,
        List<Tag> tags,
        List<Schedule> schedules
) {
    public record Patient(String cpf, String name) {}

    public record Tag(String locationId, String description, String locationName) {}

    public record Schedule(
            OffsetDateTime date,
            String time,
            String statusId,
            String scheduleId,
            String statusName,
            String locationId,
            String professionalId,
            String professionalName,
            String endTime,
            String timetableId,
            String locationName,
            @JsonProperty("schedule_procedures") List<ScheduleProcedure> scheduleProcedures,
            DarwinLocationData locationData
    ) {}

    public record ScheduleProcedure(NameOnly procedures, NameOnly insurances) {}

    public record NameOnly(String name) {}
}
