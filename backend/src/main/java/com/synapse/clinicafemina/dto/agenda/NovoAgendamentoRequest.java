package com.synapse.clinicafemina.dto.agenda;

import java.time.LocalDate;

public record NovoAgendamentoRequest(
        Long pacienteId,
        String pacienteCpf,
        String profissionalId,
        String localId,
        String timetableId,
        LocalDate data,
        String horarioInicio,
        String horarioFim,
        String procedimentoId,
        String procedimentoNome,
        String convenioId,
        String observacao
) {}
