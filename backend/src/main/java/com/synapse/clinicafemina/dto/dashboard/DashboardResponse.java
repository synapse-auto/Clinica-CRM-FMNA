package com.synapse.clinicafemina.dto.dashboard;

import java.util.List;

public record DashboardResponse(
        int equipeOnline,
        int equipeTotal,
        long novosPacientes,
        long totalMensagens,
        long consultasAgendadas,
        long confirmacoesPendentes,
        String tempoMedioResposta,
        List<HoraTotalDTO> picoMensagensPorHora,
        List<SerieDiariaDTO> pacientesSemana,
        List<SerieDiariaDTO> agendamentosSemana,
        List<ServicoDistribuicaoDTO> distribuicaoServicos,
        double taxaFidelizacao
) {
}
