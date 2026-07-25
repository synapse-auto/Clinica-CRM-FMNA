export type DarwinBackfillStatus = {
  status: 'IDLE' | 'RUNNING' | 'CONCLUIDO' | 'CANCELADO' | 'ERRO' | string;
  totalPacientes: number;
  processados: number;
  comErro: number;
  iniciadoEm: string | null;
  finalizadoEm: string | null;
};

export type DarwinStatus = {
  enabled: boolean;
  provider: string;
  configured: boolean;
  bulkSyncSupported: boolean;
  onDemandQueriesSupported: boolean;
  clinicWideListingSupported: boolean;
  localMirrorEnabled: boolean;
  knownPatientsBackfillSupported: boolean;
  coverage: string;
};
