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
import type {
  AtendenteOption,
  AtendimentoPage,
  AtendimentoResumo,
} from '@/types/atendimento';
import type { PacienteResumo } from '@/types/paciente';
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

export async function getAtendimentosIniciais(): Promise<AtendimentoPage<AtendimentoResumo>> {
  return getJson<AtendimentoPage<AtendimentoResumo>>(
    '/api/atendimentos?filtro=TODOS&tipo=TODOS&size=50',
  );
}

export async function getAtendentesAtivos(): Promise<AtendenteOption[]> {
  return getJson<AtendenteOption[]>('/api/atendimentos/atendentes');
}

export async function getPacientes(): Promise<PacienteResumo[]> {
  return getJson<PacienteResumo[]>('/api/pacientes');
}

export async function forwardBackendRequest(
  path: string,
  init: RequestInit = {},
): Promise<Response> {
  const response = await fetchBackend(path, init);
  const headers = new Headers();
  const contentType = response.headers.get('content-type') ?? 'application/json';
  headers.set('Content-Type', contentType);
  const contentDisposition = response.headers.get('content-disposition');
  if (contentDisposition) headers.set('Content-Disposition', contentDisposition);
  const contentLength = response.headers.get('content-length');
  if (contentLength) headers.set('Content-Length', contentLength);
  const body = response.status === 204 || response.status === 304
    ? null
    : await response.arrayBuffer();
  return new Response(body, {
    status: response.status,
    headers,
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
