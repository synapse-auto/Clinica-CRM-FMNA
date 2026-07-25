package com.synapse.clinicafemina.dto.darwin;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Corpo de PUT /api/schedules/update (coleção Postman Darwin v1.0.9).
 * Atualização parcial: apenas os campos enviados são alterados. Esta versão do DTO
 * não oferece mecanismo para enviar null explícito e limpar "observation" ou
 * "insuranceProcedures" (mesma simplificação conhecida do DarwinUpdatePatientRequest).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DarwinUpdateScheduleRequest(
        String scheduleId,
        String status,
        String time,
        String endTime,
        String date,
        String timetableId,
        String observation,
        List<DarwinInsuranceProcedureRef> insuranceProcedures
) {}
