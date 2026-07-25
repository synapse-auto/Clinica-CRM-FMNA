package com.synapse.clinicafemina.dto.darwin;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Corpo de POST /api/schedules/create/fitin (coleção Postman Darwin v1.0.9).
 * Encaixe — fora da grade/horário disponível; não bloqueia nem exige vaga.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DarwinCreateFitInScheduleRequest(
        String professionalId,
        String locationId,
        String status,
        String time,
        String endTime,
        String date,
        String observation,
        List<DarwinInsuranceProcedureRef> insuranceProcedures,
        List<String> tags,
        DarwinCreatePatientRequest patient
) {}
