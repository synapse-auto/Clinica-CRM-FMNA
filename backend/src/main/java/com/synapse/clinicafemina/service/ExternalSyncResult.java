package com.synapse.clinicafemina.service;

public record ExternalSyncResult(
        int pacientesProcessados,
        int pacientesCriados,
        int pacientesAtualizados,
        int agendamentosProcessados,
        int agendamentosCriados,
        int agendamentosAtualizados,
        int agendamentosIgnorados,
        String status
) {
}
