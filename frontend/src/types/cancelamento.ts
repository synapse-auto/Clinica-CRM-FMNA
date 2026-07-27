export type Cancelamento = {
  id: number;
  pacienteId: number;
  pacienteNome: string;
  telefoneMascarado: string | null;
  agendamentoId: number | null;
  dataHoraAgendamento: string | null;
  profissional: string | null;
  servico: string | null;
  motivo: string;
  origem: string;
  statusCancelamento: string;
  statusSincronizacao: string;
  coletadoEm: string;
};

export type CancelamentoPage = { content: Cancelamento[]; totalElements: number; totalPages: number; number: number };
