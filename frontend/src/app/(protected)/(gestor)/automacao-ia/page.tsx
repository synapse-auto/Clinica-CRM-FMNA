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
  nome: 'Clinica',
  slug: 'clinica',
  tipoClinica: 'PRE_NATAL',
  corPrimaria: '#0d9488',
  logoUrl: null,
  usaCirurgiasNaAgenda: true,
  followUpAutomatico: false,
  usaN8n: false,
  n8nWebhookConfigurado: false,
};

const endpointLabels = [
  '/api/follow-up/config',
  '/api/consulta-lembrete/config',
  '/api/mensagens-festivas/config',
  '/api/follow-ups-temporary',
  '/api/configuracoes/clinica-atual',
];

export default async function AutomacaoIaPage() {
  let followUps: FollowUpConfig[] = [];
  let lembretes: ConsultaLembreteConfig[] = [];
  let festivas: MensagemFestivaConfig[] = [];
  let fila: FollowUpTemporary[] = [];
  let clinic: ClinicaAtualResponse = FALLBACK_CLINIC;
  let error: string | null = null;

  const results = await Promise.allSettled([
    getFollowUpConfigs(),
    getConsultaLembreteConfigs(),
    getMensagemFestivaConfigs(),
    getFollowUpsTemporary(),
    getClinicaAtual(),
  ]);

  const failedLabels: string[] = [];
  results.forEach((result, index) => {
    if (result.status === 'rejected') {
      if (isBackendAuthorizationError(result.reason)) {
        redirect('/login');
      }
      failedLabels.push(endpointLabels[index]);
    }
  });

  if (results[0].status === 'fulfilled') followUps = results[0].value;
  if (results[1].status === 'fulfilled') lembretes = results[1].value;
  if (results[2].status === 'fulfilled') festivas = results[2].value;
  if (results[3].status === 'fulfilled') fila = results[3].value;
  if (results[4].status === 'fulfilled') clinic = results[4].value;

  if (failedLabels.length > 0) {
    error = `Nao foi possivel carregar: ${failedLabels.join(', ')}.`;
  }

  return (
    <div className="h-full overflow-auto bg-clinic-canvas p-4 custom-scrollbar">
      <AutomacaoIaClient
        initialFollowUps={followUps}
        initialLembretes={lembretes}
        initialFestivas={festivas}
        initialFila={fila}
        clinic={clinic}
        initialError={error}
      />
    </div>
  );
}
