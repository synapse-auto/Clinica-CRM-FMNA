import type { TagOperacional } from './operacional';

export type PacienteResumo = {
  id: number;
  nome: string;
  telefone: string;
  status: string;
  externalSource: string | null;
  externalId: string | null;
  origem?: string | null;
  fotoUrl: string | null;
  criadoEm: string;
  ultimaInteracaoEm: string | null;
  tags: TagOperacional[];
};

export type ImportacaoCsvContatoMapping = {
  nameColumn: string | null;
  phoneColumn: string | null;
};

export type ImportacaoCsvContatoErro = {
  rowNumber: number;
  field: string;
  code: string;
  message: string;
};

export type ImportacaoCsvContatoResumo = {
  totalRows: number;
  valid: number;
  existing: number;
  duplicateInFile: number;
  invalid: number;
  toCreate: number;
  totalErrors: number;
  errorsTruncated: boolean;
  errors: ImportacaoCsvContatoErro[];
};

export type ImportacaoCsvContatoPreview = {
  fileHash: string;
  fileName: string;
  encoding: string;
  delimiter: string;
  totalRows: number;
  headers: string[];
  suggestedMapping: ImportacaoCsvContatoMapping;
  sampleRows: Array<{ rowNumber: number; values: string[] }>;
  warnings: string[];
  validation: ImportacaoCsvContatoResumo;
};

export type ImportacaoCsvContatoResultado = {
  totalRows: number;
  created: number;
  skippedExisting: number;
  skippedDuplicateInFile: number;
  invalid: number;
  totalErrors: number;
  errorsTruncated: boolean;
  errors: ImportacaoCsvContatoErro[];
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
