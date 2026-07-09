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
