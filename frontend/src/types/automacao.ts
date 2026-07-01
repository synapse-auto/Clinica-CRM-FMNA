export type FollowUpConfig = {
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

export type FollowUpConfigPayload = {
  nome: string;
  descricao: string | null;
  ativo: boolean;
  gatilho: string;
  canal: string;
  delayQuantidade: number;
  delayUnidade: string;
  horarioEnvio: string;
  mensagemTemplate: string;
  configJson: string | null;
};

export type ConsultaLembreteConfig = {
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

export type ConsultaLembreteConfigPayload = {
  nome: string;
  descricao: string | null;
  ativo: boolean;
  canal: string;
  antecedenciaQuantidade: number;
  antecedenciaUnidade: string;
  horarioEnvio: string;
  permiteConfirmacao: boolean;
  permiteCancelamento: boolean;
  permiteReagendamento: boolean;
  mensagemTemplate: string;
  configJson: string | null;
};

export type MensagemFestivaConfig = {
  id: number;
  chave: string;
  nome: string;
  mesDia: string;
  ativo: boolean;
  canal: string;
  mensagemTemplate: string;
};

export type MensagemFestivaConfigPayload = {
  chave: string;
  nome: string;
  mesDia: string;
  ativo: boolean;
  canal: string;
  mensagemTemplate: string;
  configJson: string | null;
};

export type FollowUpTemporary = {
  id: number;
  pacienteId: number;
  titulo: string;
  origem: string;
  status: string;
  scheduledAt: string;
};
