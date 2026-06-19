import 'server-only';

import { cookies } from 'next/headers';
import {
  demoConsultaLembretes,
  demoFollowUpConfigs,
  demoFollowUpsTemporary,
  demoMensagensFestivas,
  type DemoConsultaLembreteConfig,
  type DemoFollowUpConfig,
  type DemoFollowUpTemporary,
  type DemoMensagemFestivaConfig,
} from '@/mocks/demoAutomacao';
import { demoDashboardFallback } from '@/mocks/demoDashboard';
import type {
  ClinicaAtualResponse,
  DashboardPeriodo,
  DashboardResponse,
} from '@/types/dashboard';
import type { Agendamento, AgendaOptions } from '@/types/agendamento';
import { SESSION_COOKIE_NAME } from '@/lib/auth/constants';

const API_BASE_URL =
  process.env.BACKEND_API_URL ??
  process.env.NEXT_PUBLIC_API_BASE_URL ??
  'http://localhost:8080';

const DEFAULT_CLINICA: ClinicaAtualResponse = {
  nome: 'Clínica',
  slug: 'clinica',
  tipoClinica: 'PRE_NATAL',
  corPrimaria: '#0d9488',
  logoUrl: null,
  usaCirurgiasNaAgenda: true,
  followUpAutomatico: false,
};

export async function getDashboardData(
  periodo: DashboardPeriodo,
  data: string,
): Promise<DashboardResponse> {
  const params = new URLSearchParams({ periodo, data });
  return getJsonOrFallback(`/api/dashboard?${params.toString()}`, demoDashboardFallback);
}

export async function getClinicaAtual(): Promise<ClinicaAtualResponse> {
  return getJsonOrFallback('/api/configuracoes/clinica-atual', DEFAULT_CLINICA);
}

export async function getFollowUpConfigs(): Promise<DemoFollowUpConfig[]> {
  return getJsonOrFallback('/api/follow-up/config', demoFollowUpConfigs);
}

export async function getConsultaLembreteConfigs(): Promise<DemoConsultaLembreteConfig[]> {
  return getJsonOrFallback('/api/consulta-lembrete/config', demoConsultaLembretes);
}

export async function getMensagemFestivaConfigs(): Promise<DemoMensagemFestivaConfig[]> {
  return getJsonOrFallback('/api/mensagens-festivas/config', demoMensagensFestivas);
}

export async function getFollowUpsTemporary(): Promise<DemoFollowUpTemporary[]> {
  const response = await getJsonOrFallback<{ content?: DemoFollowUpTemporary[] } | DemoFollowUpTemporary[]>(
    '/api/follow-ups-temporary',
    demoFollowUpsTemporary,
  );

  if (Array.isArray(response)) {
    return response;
  }
  return response.content ?? demoFollowUpsTemporary;
}

export async function getAgendamentos(
  inicio: string,
  fim: string,
): Promise<Agendamento[]> {
  const params = new URLSearchParams({ inicio, fim });
  return getJson<Agendamento[]>(`/api/agendamentos?${params.toString()}`);
}

export async function getAgendaOptions(): Promise<AgendaOptions> {
  return getJson<AgendaOptions>('/api/agendamentos/opcoes');
}

export async function forwardBackendRequest(
  path: string,
  init: RequestInit = {},
): Promise<Response> {
  const response = await fetchBackend(path, init);
  const contentType = response.headers.get('content-type') ?? 'application/json';
  return new Response(await response.text(), {
    status: response.status,
    headers: { 'Content-Type': contentType },
  });
}

async function getJsonOrFallback<T>(path: string, fallback: T): Promise<T> {
  try {
    return await getJson<T>(path);
  } catch (error) {
    if (error instanceof BackendAuthorizationError) throw error;
    return fallback;
  }
}

async function getJson<T>(path: string): Promise<T> {
  const response = await fetchBackend(path);

  if (!response.ok) {
    if (response.status === 401 || response.status === 403) {
      throw new BackendAuthorizationError(response.status);
    }
    throw new Error(`Backend respondeu ${response.status} para ${path}`);
  }

  return response.json() as Promise<T>;
}

async function fetchBackend(path: string, init: RequestInit = {}): Promise<Response> {
  const cookieStore = await cookies();
  const requestHeaders = new Headers(init.headers);
  requestHeaders.set('Accept', 'application/json');

  const token = cookieStore.get(SESSION_COOKIE_NAME)?.value;
  if (token) {
    requestHeaders.set('Authorization', `Bearer ${token}`);
  }

  return fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: requestHeaders,
    cache: 'no-store',
  });
}

class BackendAuthorizationError extends Error {
  constructor(readonly status: number) {
    super(`Backend recusou a sessão (${status})`);
  }
}
