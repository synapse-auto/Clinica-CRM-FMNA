import { AgendaClient } from '@/components/agenda/AgendaClient';
import { redirect } from 'next/navigation';
import {
  getAgendaCapabilities,
  getAgendaFmna,
  getAgendaProfissionaisSSR,
  isBackendAuthorizationError,
} from '@/services/backend';
import type { AgendaAgendamento, AgendaCapabilities, AgendaProfissional } from '@/types/agenda';

// Capacidades conservadoras usadas somente quando GET /api/agenda/capabilities falha —
// nunca inferidas do catalogo. Sem catalogo/fitIn/escrita ate que o endpoint responda.
const FALLBACK_CAPABILITIES: AgendaCapabilities = {
  provider: 'DESCONHECIDO',
  supportsCatalog: false,
  supportsWriteOperations: false,
  supportsFitIn: false,
  supportsClinicWideListing: false,
  supportsPatientLookup: false,
  supportsBackfill: false,
  coverage: 'DESCONHECIDA',
};

export default async function AgendaPage() {
  const range = getCurrentWeekRange();
  let appointments: AgendaAgendamento[] = [];
  let profissionais: AgendaProfissional[] = [];
  let capabilities: AgendaCapabilities = FALLBACK_CAPABILITIES;
  let error: string | null = null;

  try {
    appointments = await getAgendaFmna(range.inicio, range.fim);
  } catch (caughtError) {
    if (isBackendAuthorizationError(caughtError)) {
      redirect('/login');
    }
    error = 'Não foi possível carregar a agenda. Verifique a conexão e tente novamente.';
  }

  if (!error) {
    try {
      capabilities = await getAgendaCapabilities();
    } catch (caughtError) {
      if (isBackendAuthorizationError(caughtError)) {
        redirect('/login');
      }
      // Endpoint de capacidades indisponivel — mantem o fallback conservador
      // (sem catalogo/fitIn/escrita); nunca inferido do catalogo.
    }

    if (capabilities.supportsCatalog) {
      try {
        profissionais = await getAgendaProfissionaisSSR();
      } catch (caughtError) {
        if (isBackendAuthorizationError(caughtError)) {
          redirect('/login');
        }
        // Falha transitoria do catalogo nao altera as capacidades ja resolvidas acima.
        profissionais = [];
      }
    }
  }

  return (
    <AgendaClient
      initialAppointments={appointments}
      initialProfissionais={profissionais}
      initialCapabilities={capabilities}
      initialError={error}
      weekStart={range.weekStart}
    />
  );
}

function getCurrentWeekRange() {
  const nowInSaoPaulo = new Date(new Date().toLocaleString('en-US', {
    timeZone: 'America/Sao_Paulo',
  }));
  const start = new Date(nowInSaoPaulo);
  const day = start.getDay();
  start.setDate(start.getDate() - (day === 0 ? 6 : day - 1));
  const end = new Date(start);
  end.setDate(start.getDate() + 5);
  const weekStart = formatLocalDate(start);
  return {
    weekStart,
    inicio: `${weekStart}T00:00:00-03:00`,
    fim: `${formatLocalDate(end)}T00:00:00-03:00`,
  };
}

function formatLocalDate(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}
