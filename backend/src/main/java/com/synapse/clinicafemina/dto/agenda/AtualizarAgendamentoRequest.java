package com.synapse.clinicafemina.dto.agenda;

import java.time.LocalDate;

public record AtualizarAgendamentoRequest(
        String status,
        LocalDate data,
        String horarioInicio,
        String horarioFim,
        String timetableId,
        String procedimentoId,
        String convenioId,
        String observacao
) {}
