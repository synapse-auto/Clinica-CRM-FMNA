package com.synapse.clinicafemina.dto.atendimento;

import java.time.LocalDate;
import java.time.LocalTime;

public record AtendimentoLembreteRequest(
        LocalDate data,
        LocalTime hora,
        String mensagem
) {
}
