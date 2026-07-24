package com.synapse.clinicafemina.dto.darwin;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Corpo de resposta de GET /api/timetables/list/available (coleção Postman Darwin v1.0.9).
 */
public record DarwinAvailableTimetablesResponse(
        String minimumOnlineScheduleAdvanceTime,
        List<ProfessionalAvailableTimes> professionalsAvailableTimes
) {
    public record ProfessionalAvailableTimes(
            String professionalId,
            String professionalName,
            List<AvailableTimetable> timetables
    ) {}

    public record AvailableTimetable(
            String timetableId,
            String locationId,
            String locationName,
            DarwinLocationData locationData,
            List<AvailableProcedure> procedures,
            List<DarwinNamedRef> insurances,
            OffsetDateTime date,
            List<AvailableTime> availableTimes
    ) {}

    public record AvailableProcedure(
            String id,
            String name,
            Integer timeSpent
    ) {}

    public record AvailableTime(
            String startTime,
            String endTime
    ) {}
}
