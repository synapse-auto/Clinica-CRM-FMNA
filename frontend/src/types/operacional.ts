export type TagOperacional = {
  id: number;
  nome: string;
  cor: string;
  descricao: string | null;
  ativo: boolean;
  criadoEm: string | null;
  atualizadoEm: string | null;
};

export type TagPayload = {
  nome: string;
  cor: string;
  descricao?: string | null;
  ativo: boolean;
};

export type CategoriaMensagemRapida = {
  id: number;
  codigo: string;
  rotulo: string;
  cor: string | null;
};

export type MensagemRapida = {
  id: number;
  categoriaId: number | null;
  categoriaRotulo: string | null;
  categoriaCor: string | null;
  titulo: string;
  atalho: string;
  conteudo: string;
  ativo: boolean;
  criadoEm: string | null;
  atualizadoEm: string | null;
};

export type MensagemRapidaPayload = {
  categoriaId?: number | null;
  titulo: string;
  atalho: string;
  conteudo: string;
  ativo: boolean;
};

export type HorarioAtendente = {
  id: number;
  usuarioId: number;
  usuarioNome: string;
  diaSemana: number;
  horaInicio: string;
  horaFim: string;
  ativo: boolean;
  criadoEm: string | null;
  atualizadoEm: string | null;
};

export type HorarioAtendentePayload = {
  usuarioId: number;
  diaSemana: number;
  horaInicio: string;
  horaFim: string;
  ativo: boolean;
};
