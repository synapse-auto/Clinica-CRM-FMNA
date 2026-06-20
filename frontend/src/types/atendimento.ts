export type AtendimentoPage<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
};

export type AtendenteOption = {
  id: number;
  nome: string;
  perfil: string;
};

export type AtendimentoResumo = {
  id: number;
  status: string;
  tratadoPorIa: boolean;
  ultimaMensagemEm: string | null;
  naoLidas: number;
  ultimaMensagemPrevia: string;
  requerRevisao: boolean;
  convenioStatus: string | null;
  paciente: {
    id: number;
    nomeBusca: string;
    telefoneNormalizado: string;
  };
  atendentePrincipal: {
    id: number;
    nome: string;
  } | null;
};

export type AtendimentoDetalhe = {
  id: number;
  status: string;
  tratadoPorIa: boolean;
  dataInicio: string;
  dataEncerramento: string | null;
  naoLidas: number;
  paciente: {
    id: number;
    nome: string;
    telefone: string;
    email: string | null;
    status: string;
    ultimaInteracaoEm: string | null;
    requerRevisao: boolean;
    convenioStatus: string | null;
    convenioRevisadoEm: string | null;
    convenioRevisadoPorId: number | null;
    convenioRevisadoPorNome: string | null;
  };
  atendentePrincipal: {
    id: number;
    nome: string;
    perfil: string;
  } | null;
};

export type MensagemAtendimento = {
  id: number;
  direcao: 'ENTRADA' | 'SAIDA';
  remetente: 'PACIENTE' | 'ATENDENTE' | 'IA' | 'SISTEMA';
  tipoMedia: 'TEXTO' | 'IMAGEM' | 'AUDIO' | 'DOCUMENTO' | 'TEMPLATE';
  conteudo: string | null;
  conteudoPrevia: string | null;
  whatsappStatus: string | null;
  motivoFalha: string | null;
  dataHora: string;
  entregueEm: string | null;
  lidaEm: string | null;
  midia: {
    tipoMedia: string;
    mimeType: string;
    nomeArquivo: string | null;
    tamanhoBytes: number;
    url: string;
  } | null;
};

export type AtendimentoFilter =
  | 'TODOS'
  | 'MEUS'
  | 'NAO_LIDOS'
  | 'AGUARDANDO'
  | 'FINALIZADOS'
  | 'REVISAO';

export type NotificacaoAtendimento = {
  id: number;
  atendimentoId: number;
  tipo: 'NOVA_MENSAGEM' | 'ATENDIMENTO_ATRIBUIDO';
  descricao: string;
  lida: boolean;
  criadoEm: string;
};
