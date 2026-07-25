/**
 * Tipos normalizados da Agenda provider-agnostic (/api/agenda). Espelham exatamente os
 * DTOs Java em backend/src/main/java/com/synapse/clinicafemina/dto/agenda — o frontend
 * não conhece Medware/Darwin, apenas estes campos normalizados.
 */

export type AgendaSyncStatus = 'SYNCED' | 'PENDING_RECONCILIATION' | 'FAILED' | 'CANCELLED';

export type AgendaCapabilities = {
  provider: string;
  supportsCatalog: boolean;
  supportsWriteOperations: boolean;
  supportsFitIn: boolean;
  supportsClinicWideListing: boolean;
  supportsPatientLookup: boolean;
  supportsBackfill: boolean;
  coverage: string;
};

export type AgendaAgendamento = {
  idLocal: number;
  externalId: string | null;
  provider: string;
  pacienteId: number | null;
  pacienteNome: string;
  pacienteCpfMascarado: string | null;
  profissionalId: string | null;
  profissionalNome: string | null;
  procedimentoId: string | null;
  procedimentoNome: string | null;
  convenioId: string | null;
  convenioNome: string | null;
  localId: string | null;
  localNome: string | null;
  data: string;
  horarioInicio: string | null;
  horarioFim: string | null;
  status: string;
  timetableId: string | null;
  observacao: string | null;
  origem: string | null;
  lastSyncedAt: string | null;
  syncStatus: AgendaSyncStatus | string;
};

export type AgendaProfissional = {
  id: string;
  nome: string;
  origem: string | null;
};

export type AgendaHorarioDisponivel = {
  timetableId: string;
  profissionalId: string | null;
  profissionalNome: string | null;
  localId: string | null;
  localNome: string | null;
  data: string;
  horarioInicio: string;
  horarioFim: string;
};

export type AgendaProcedimento = {
  id: string;
  nome: string;
};

export type AgendaConvenio = {
  id: string;
  nome: string;
};

export type AgendaLocal = {
  id: string;
  nome: string;
};

export type AgendaPaciente = {
  id: number;
  nome: string;
  cpfMascarado: string | null;
};

export type NovoPacientePayload = {
  cpf: string;
  nome: string;
  telefone: string | null;
  email: string | null;
  dataNascimento: string | null;
};

export type NovoAgendamentoPayload = {
  pacienteId: number | null;
  pacienteCpf: string | null;
  profissionalId: string | null;
  localId: string | null;
  timetableId: string | null;
  data: string;
  horarioInicio: string;
  horarioFim: string | null;
  procedimentoId: string | null;
  procedimentoNome: string | null;
  convenioId: string | null;
  observacao: string | null;
};

export type AtualizarAgendamentoPayload = {
  status: string | null;
  data: string | null;
  horarioInicio: string | null;
  horarioFim: string | null;
  timetableId: string | null;
  procedimentoId: string | null;
  convenioId: string | null;
  observacao: string | null;
};
