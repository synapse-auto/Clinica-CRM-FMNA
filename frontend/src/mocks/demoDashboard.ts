import type { DashboardResponse } from '@/types/dashboard';

export const demoDashboardFallback: DashboardResponse = {
  equipeOnline: 3,
  equipeTotal: 5,
  novosPacientes: 12,
  totalMensagens: 547,
  consultasAgendadas: 47,
  confirmacoesPendentes: 4,
  tempoMedioResposta: '4,2min',
  picoMensagensPorHora: [
    { hora: 0, total: 2 },
    { hora: 4, total: 0 },
    { hora: 8, total: 22 },
    { hora: 12, total: 48 },
    { hora: 16, total: 36 },
    { hora: 20, total: 14 },
  ],
  pacientesSemana: [
    { data: '2026-06-11', total: 4 },
    { data: '2026-06-12', total: 6 },
    { data: '2026-06-13', total: 5 },
    { data: '2026-06-14', total: 8 },
    { data: '2026-06-15', total: 7 },
    { data: '2026-06-16', total: 9 },
    { data: '2026-06-17', total: 12 },
  ],
  agendamentosSemana: [
    { data: '2026-06-11', total: 9 },
    { data: '2026-06-12', total: 12 },
    { data: '2026-06-13', total: 8 },
    { data: '2026-06-14', total: 10 },
    { data: '2026-06-15', total: 14 },
    { data: '2026-06-16', total: 11 },
    { data: '2026-06-17', total: 13 },
  ],
  distribuicaoServicos: [
    { servico: 'Consulta Pre-natal', total: 18, percentual: 38.3 },
    { servico: 'Ultrassom', total: 14, percentual: 29.8 },
    { servico: 'Retorno', total: 9, percentual: 19.1 },
    { servico: 'Exames', total: 6, percentual: 12.8 },
  ],
  taxaFidelizacao: 66.7,
};
