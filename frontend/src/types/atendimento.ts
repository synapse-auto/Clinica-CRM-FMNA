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
    fotoUrl: string | null;
  };
  atendentePrincipal: {
    id: number;
    nome: string;
  } | null;
  tags: AtendimentoTagResumo[];
};

export type AtendimentoTagResumo = {
  id: number;
  nome: string;
  cor: string | null;
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
    fotoUrl: string | null;
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
  janelaWhatsappAberta: boolean | null;
  janelaWhatsappExpiraEm: string | null;
  ultimaMensagemEntradaEm: string | null;
  aguardandoRespostaTemplate: boolean | null;
  whatsappTemplatesDisponiveis: boolean | null;
};

export type WhatsappTemplateStatus = 'APPROVED' | 'PENDING' | 'PAUSED' | 'REJECTED' | string;

export type WhatsappTemplateComponent = 'HEADER' | 'BODY' | 'BUTTON';

export type WhatsappTemplateVariable = {
  componente: WhatsappTemplateComponent;
  posicao: number;
  indiceBotao: number | null;
};

export type WhatsappTemplateButton = {
  tipo: string;
  texto: string;
  url: string | null;
};

export type WhatsappTemplate = {
  id: string;
  nome: string;
  idioma: string;
  status: WhatsappTemplateStatus;
  categoria: string;
  cabecalho: string | null;
  corpo: string | null;
  rodape: string | null;
  botoes: WhatsappTemplateButton[];
  variaveis: WhatsappTemplateVariable[];
  suportado: boolean;
  motivoNaoSuportado: string | null;
};

export type EnviarTemplateWhatsappRequest = {
  nome: string;
  idioma: string;
  parametros: Array<WhatsappTemplateVariable & { valor: string }>;
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
  templateNome: string | null;
  templateIdioma: string | null;
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

export type AtendimentoLembreteStatus = 'PENDENTE' | 'CONCLUIDO' | 'CANCELADO';

export type AtendimentoLembrete = {
  id: number;
  atendimentoId: number;
  mensagem: string;
  lembrarEm: string;
  status: AtendimentoLembreteStatus;
  criadoPorId: number | null;
  criadoPorNome: string | null;
  criadoEm: string | null;
  atualizadoEm: string | null;
};

export type NovoAtendimentoLembrete = {
  data: string;
  hora: string;
  mensagem: string;
};
