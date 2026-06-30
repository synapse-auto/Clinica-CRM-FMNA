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

export type MensagemFestivaConfig = {
  id: number;
  chave: string;
  nome: string;
  mesDia: string;
  ativo: boolean;
  canal: string;
  mensagemTemplate: string;
};

export type FollowUpTemporary = {
  id: number;
  pacienteId: number;
  titulo: string;
  origem: string;
  status: string;
  scheduledAt: string;
};
