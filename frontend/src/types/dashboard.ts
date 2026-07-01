export type DashboardPeriodo = 'DIA' | 'SEMANA' | 'MES';

export type HoraTotalDTO = {
  hora: number;
  total: number;
};

export type SerieDiariaDTO = {
  data: string;
  total: number;
};

export type ServicoDistribuicaoDTO = {
  servico: string;
  total: number;
  percentual: number;
};

export type DashboardResponse = {
  equipeOnline: number;
  equipeTotal: number;
  novosPacientes: number;
  totalMensagens: number;
  consultasAgendadas: number;
  confirmacoesPendentes: number;
  tempoMedioResposta: string;
  picoMensagensPorHora: HoraTotalDTO[];
  pacientesSemana: SerieDiariaDTO[];
  agendamentosSemana: SerieDiariaDTO[];
  distribuicaoServicos: ServicoDistribuicaoDTO[];
  taxaFidelizacao: number;
};

export type ClinicaAtualResponse = {
  nome: string;
  slug: string;
  tipoClinica: 'PRE_NATAL' | 'ULTRASSONOGRAFIA';
  corPrimaria?: string | null;
  logoUrl?: string | null;
  usaCirurgiasNaAgenda: boolean;
  followUpAutomatico: boolean;
  usaN8n?: boolean;
  n8nWebhookConfigurado?: boolean;
};
