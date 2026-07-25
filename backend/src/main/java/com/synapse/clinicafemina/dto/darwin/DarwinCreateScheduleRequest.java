package com.synapse.clinicafemina.dto.darwin;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Corpo de POST /api/schedules/create (coleção Postman Darwin v1.0.9).
 * Agendamento normal — exige timetableId (grade) e horário disponível.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DarwinCreateScheduleRequest(
        String timetableId,
        String status,
        String time,
        String endTime,
        String date,
        String observation,
        List<DarwinInsuranceProcedureRef> insuranceProcedures,
        List<String> tags,
        DarwinCreatePatientRequest patient
) {}
