import type { TagOperacional } from './operacional';

export type PacienteResumo = {
  id: number;
  nome: string;
  telefone: string;
  status: string;
  externalSource: string | null;
  externalId: string | null;
  fotoUrl: string | null;
  criadoEm: string;
  ultimaInteracaoEm: string | null;
  tags: TagOperacional[];
};

export type PacienteStatusCounts = {
  total: number;
  emAtendimento: number;
  agendado: number;
  finalizado: number;
  outros: number;
};

export type PacientePage = {
  content: PacienteResumo[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  counts: PacienteStatusCounts;
};
