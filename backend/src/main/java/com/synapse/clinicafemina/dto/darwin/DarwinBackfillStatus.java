package com.synapse.clinicafemina.dto.darwin;

import java.time.OffsetDateTime;

/**
 * Status do job administrativo de backfill de agendamentos Darwin
 * (GET /api/admin/integracoes/darwin/backfill-agendamentos/status).
 * Nunca inclui CPF, nome de paciente ou qualquer dado clínico — apenas contadores.
 */
public record DarwinBackfillStatus(
        String status,
        int totalPacientes,
        int processados,
        int comErro,
        OffsetDateTime iniciadoEm,
        OffsetDateTime finalizadoEm
) {
    public static DarwinBackfillStatus idle() {
        return new DarwinBackfillStatus("IDLE", 0, 0, 0, null, null);
    }

    public DarwinBackfillStatus comProgresso(int processados, int comErro) {
        return new DarwinBackfillStatus(status, totalPacientes, processados, comErro, iniciadoEm, finalizadoEm);
    }

    public DarwinBackfillStatus comStatus(String novoStatus) {
        return new DarwinBackfillStatus(novoStatus, totalPacientes, processados, comErro, iniciadoEm, finalizadoEm);
    }

    public DarwinBackfillStatus finalizadoAgora() {
        return new DarwinBackfillStatus(status, totalPacientes, processados, comErro, iniciadoEm, OffsetDateTime.now());
    }
}
