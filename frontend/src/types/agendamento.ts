export type Agendamento = {
  id: number;
  pacienteId: number;
  pacienteNome: string;
  medicoId: number | null;
  medicoNome: string | null;
  medicoExternalId: string | null;
  medicoOrigem: string | null;
  dataHoraInicio: string;
  dataHoraFim: string | null;
  tipo: string;
  servicoNome: string;
  status: string;
  origem: string;
  confirmacaoEnviada: number | null;
  canceladoEm: string | null;
  motivoCancelamento: string | null;
};

export type AgendaOption = {
  id: number | null;
  nome: string;
  codigoExterno: string | null;
  origem: string | null;
};

export type AgendaOptions = {
  pacientes: AgendaOption[];
  medicos: AgendaOption[];
};

export type AgendamentoPayload = {
  pacienteId: number;
  medicoId: number | null;
  dataHoraInicio: string;
  dataHoraFim: string | null;
  tipo: string;
  servicoNome: string;
};
