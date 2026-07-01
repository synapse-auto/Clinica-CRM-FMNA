import { redirect } from 'next/navigation';
import { AutomacaoIaClient } from '@/components/automacao/AutomacaoIaClient';
import {
  getClinicaAtual,
  getConsultaLembreteConfigs,
  getFollowUpConfigs,
  getFollowUpsTemporary,
  getMensagemFestivaConfigs,
  isBackendAuthorizationError,
} from '@/services/backend';
import type {
  ConsultaLembreteConfig,
  FollowUpConfig,
  FollowUpTemporary,
  MensagemFestivaConfig,
} from '@/types/automacao';
import type { ClinicaAtualResponse } from '@/types/dashboard';

const FALLBACK_CLINIC: ClinicaAtualResponse = {
  nome: 'Clínica',
  slug: 'clinica',
  tipoClinica: 'PRE_NATAL',
  corPrimaria: '#0d9488',
  logoUrl: null,
  usaCirurgiasNaAgenda: true,
  followUpAutomatico: false,
  usaN8n: false,
  n8nWebhookConfigurado: false,
};

export default async function AutomacaoIaPage() {
  let followUps: FollowUpConfig[] = [];
  let lembretes: ConsultaLembreteConfig[] = [];
  let festivas: MensagemFestivaConfig[] = [];
  let fila: FollowUpTemporary[] = [];
  let clinic: ClinicaAtualResponse | null = null;
  let error: string | null = null;

  try {
    [followUps, lembretes, festivas, fila, clinic] = await Promise.all([
      getFollowUpConfigs(),
      getConsultaLembreteConfigs(),
      getMensagemFestivaConfigs(),
      getFollowUpsTemporary(),
      getClinicaAtual(),
    ]);
  } catch (caughtError) {
    if (isBackendAuthorizationError(caughtError)) {
      redirect('/login');
    }
    error = 'Não foi possível carregar as automações. Verifique a conexão com o servidor.';
    try {
      clinic = await getClinicaAtual();
    } catch {
      clinic = FALLBACK_CLINIC;
    }
  }

  return (
    <div className="h-full overflow-auto bg-clinic-canvas p-4 custom-scrollbar">
      <AutomacaoIaClient
        initialFollowUps={followUps}
        initialLembretes={lembretes}
        initialFestivas={festivas}
        initialFila={fila}
        clinic={clinic ?? FALLBACK_CLINIC}
        initialError={error}
      />
    </div>
  );
}
