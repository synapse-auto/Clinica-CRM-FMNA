export type DemoFollowUpConfig = {
  id: number;
  nome: string;
  descricao: string;
  ativo: boolean;
  gatilho: string;
  canal: string;
  delayQuantidade?: number;
  delayUnidade?: string;
  horarioEnvio?: string;
  mensagemTemplate?: string;
};

export type DemoConsultaLembreteConfig = {
  id: number;
  nome: string;
  descricao: string;
  ativo: boolean;
  canal: string;
  antecedenciaQuantidade: number;
  antecedenciaUnidade: string;
  horarioEnvio?: string;
  permiteConfirmacao: boolean;
  permiteCancelamento: boolean;
  permiteReagendamento: boolean;
  mensagemTemplate?: string;
};

export type DemoMensagemFestivaConfig = {
  id: number;
  chave: string;
  nome: string;
  mesDia: string;
  ativo: boolean;
  canal: string;
  mensagemTemplate: string;
};

export type DemoFollowUpTemporary = {
  id: number;
  pacienteId: number;
  titulo: string;
  origem: string;
  status: string;
  scheduledAt: string;
};

export const demoFollowUpConfigs: DemoFollowUpConfig[] = [
  {
    id: 1,
    nome: 'Pos-consulta - avaliacao',
    descricao: 'Solicitacao de avaliacao enviada apos atendimento',
    ativo: true,
    gatilho: 'POS_CONSULTA',
    canal: 'WHATSAPP',
    delayQuantidade: 1,
    delayUnidade: 'DIAS',
    horarioEnvio: '09:00',
    mensagemTemplate: 'Ola, [Nome]! Esperamos que sua consulta tenha sido otima. Avalie nosso atendimento.',
  },
  {
    id: 2,
    nome: 'Reativacao de pacientes inativos',
    descricao: 'Para pacientes que nao retornaram apos 3 meses',
    ativo: true,
    gatilho: 'REATIVACAO',
    canal: 'WHATSAPP',
    delayQuantidade: 90,
    delayUnidade: 'DIAS',
    horarioEnvio: '09:00',
    mensagemTemplate: 'Oi, [Nome]! Sentimos sua falta. Temos horarios disponiveis esta semana.',
  },
];

export const demoConsultaLembretes: DemoConsultaLembreteConfig[] = [
  {
    id: 1,
    nome: 'Lembrete - 48h antes',
    descricao: 'Mensagem automatica dois dias antes da consulta',
    ativo: true,
    canal: 'WHATSAPP',
    antecedenciaQuantidade: 48,
    antecedenciaUnidade: 'HORAS',
    horarioEnvio: '08:00',
    permiteConfirmacao: true,
    permiteCancelamento: true,
    permiteReagendamento: true,
    mensagemTemplate: 'Ola, [Nome]! Lembramos que voce tem consulta com [Medico] em [Data] as [Horario].',
  },
  {
    id: 2,
    nome: 'Lembrete - 24h antes',
    descricao: 'Confirmacao final no dia anterior',
    ativo: true,
    canal: 'WHATSAPP',
    antecedenciaQuantidade: 24,
    antecedenciaUnidade: 'HORAS',
    horarioEnvio: '08:00',
    permiteConfirmacao: true,
    permiteCancelamento: true,
    permiteReagendamento: false,
    mensagemTemplate: 'Sua consulta e amanha. Responda SIM para confirmar ou NAO para remarcar.',
  },
];

export const demoMensagensFestivas: DemoMensagemFestivaConfig[] = [
  {
    id: 1,
    chave: 'DIA_DAS_MAES',
    nome: 'Dia das Maes',
    mesDia: '05-12',
    ativo: true,
    canal: 'WHATSAPP',
    mensagemTemplate: 'Feliz Dia das Maes! Que seu dia seja cheio de cuidado e carinho.',
  },
  {
    id: 2,
    chave: 'NATAL',
    nome: 'Natal',
    mesDia: '12-25',
    ativo: false,
    canal: 'WHATSAPP',
    mensagemTemplate: 'Feliz Natal! A equipe deseja saude e bons momentos para voce e sua familia.',
  },
];

export const demoFollowUpsTemporary: DemoFollowUpTemporary[] = [
  {
    id: 1,
    pacienteId: 42,
    titulo: 'Follow-up de avaliacao',
    origem: 'FOLLOW_UP_CONFIG',
    status: 'PENDENTE',
    scheduledAt: '2026-06-17T13:00:00-03:00',
  },
  {
    id: 2,
    pacienteId: 51,
    titulo: 'Reativacao trimestral',
    origem: 'N8N',
    status: 'PROCESSANDO',
    scheduledAt: '2026-06-17T15:30:00-03:00',
  },
];
