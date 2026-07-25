package com.synapse.clinicafemina.dto.agenda;

import java.time.LocalDate;

public record AgendaHorarioDisponivelDTO(
        String timetableId,
        String profissionalId,
        String profissionalNome,
        String localId,
        String localNome,
        LocalDate data,
        String horarioInicio,
        String horarioFim
) {}
