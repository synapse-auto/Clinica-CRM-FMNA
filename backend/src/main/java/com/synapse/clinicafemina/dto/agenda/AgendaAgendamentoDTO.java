package com.synapse.clinicafemina.dto.agenda;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Agendamento normalizado retornado pela Agenda do CRM, independente do provider
 * (Medware ou Darwin) que originou o dado.
 */
public record AgendaAgendamentoDTO(
        Long idLocal,
        String externalId,
        String provider,
        Long pacienteId,
        String pacienteNome,
        String pacienteCpfMascarado,
        String profissionalId,
        String profissionalNome,
        String procedimentoId,
        String procedimentoNome,
        String convenioId,
        String convenioNome,
        String localId,
        String localNome,
        LocalDate data,
        String horarioInicio,
        String horarioFim,
        String status,
        String timetableId,
        String observacao,
        String origem,
        OffsetDateTime lastSyncedAt,
        String syncStatus
) {}
