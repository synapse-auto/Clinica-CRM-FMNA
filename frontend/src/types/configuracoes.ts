export type ConfiguracaoResumo = {
  identidade: {
    nome: string;
    slug: string;
    tipoClinica: 'PRE_NATAL' | 'ULTRASSONOGRAFIA';
    externalProvider: string;
    statusOperacional: string;
    whatsappConfigurado: boolean;
    n8nConfigurado: boolean;
  };
  integracoes: Array<{
    nome: string;
    status: string;
    detalhe: string;
  }>;
  ultimaSincronizacaoMedware: {
    status: string;
    iniciadoEm: string | null;
    concluidoEm: string | null;
    dataInicio: string | null;
    dataFim: string | null;
    pacientesProcessados: number;
    agendamentosProcessados: number;
    agendamentosIgnorados: number;
    erroResumo: string | null;
  } | null;
  seguranca: {
    perfisAtivos: Array<{
      perfil: string;
      total: number;
    }>;
    regras: string[];
  };
  operacao: {
    horariosConfigurados: boolean;
    iaAtiva: boolean;
    retornoHumanoIa24h: boolean;
    agendaMedicoSomenteLeitura: boolean;
    mutacaoAgendaRestrita: boolean;
  };
  ambiente: {
    nome: string;
    inicializadoEm: string;
  };
};
