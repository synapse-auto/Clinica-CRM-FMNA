import 'server-only';

import { headers } from 'next/headers';
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

async function getJsonOrFallback<T>(path: string, fallback: T): Promise<T> {
  try {
    return await getJson<T>(path);
  } catch {
    return fallback;
  }
}

async function getJson<T>(path: string): Promise<T> {
  const incomingHeaders = await headers();
  const requestHeaders: HeadersInit = {
    Accept: 'application/json',
  };

  const cookie = incomingHeaders.get('cookie');
  const authorization = incomingHeaders.get('authorization');
  const backendToken = process.env.BACKEND_API_TOKEN;

  if (cookie) {
    requestHeaders.Cookie = cookie;
  }
  if (authorization) {
    requestHeaders.Authorization = authorization;
  } else if (backendToken) {
    requestHeaders.Authorization = `Bearer ${backendToken}`;
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: requestHeaders,
    cache: 'no-store',
  });

  if (!response.ok) {
    throw new Error(`Backend respondeu ${response.status} para ${path}`);
  }

  return response.json() as Promise<T>;
}
