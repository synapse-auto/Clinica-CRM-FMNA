package com.synapse.clinicafemina.dto.darwin;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Item de resposta de GET /api/timetables/list/professional (coleção Postman Darwin v1.0.9).
 */
public record DarwinProfessionalTimetableDTO(
        String id,
        boolean monday,
        boolean tuesday,
        boolean wednesday,
        boolean thursday,
        boolean friday,
        boolean saturday,
        boolean sunday,
        @JsonProperty("start_time") String startTime,
        @JsonProperty("end_time") String endTime,
        @JsonProperty("interval_time") String intervalTime,
        @JsonProperty("start_datetime") OffsetDateTime startDatetime,
        @JsonProperty("end_datetime") OffsetDateTime endDatetime,
        @JsonProperty("location_id") String locationId,
        boolean isOnlineAvailable,
        LocationRef locations,
        List<ProcedureWrapper> timetableProcedures,
        List<InsuranceWrapper> timetableInsurances,
        List<ProcedureWrapper> timetableOnlineProcedures,
        List<InsuranceWrapper> timetableOnlineInsurances
) {
    public record LocationRef(@JsonProperty("location_name") String locationName) {}

    public record ProcedureWrapper(DarwinNamedRef procedures) {}

    public record InsuranceWrapper(DarwinNamedRef insurances) {}
}
