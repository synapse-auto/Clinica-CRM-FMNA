import type { DarwinBackfillStatus, DarwinStatus } from '@/types/darwin';

export async function getDarwinStatus(): Promise<DarwinStatus> {
  return requestJson('/api/integracoes/darwin/status');
}

export async function getBackfillStatus(): Promise<DarwinBackfillStatus> {
  return requestJson('/api/admin/integracoes/darwin/backfill-agendamentos/status');
}

export async function iniciarBackfill(): Promise<DarwinBackfillStatus> {
  return requestJson('/api/admin/integracoes/darwin/backfill-agendamentos', { method: 'POST' });
}

export async function cancelarBackfill(): Promise<DarwinBackfillStatus> {
  return requestJson('/api/admin/integracoes/darwin/backfill-agendamentos', { method: 'DELETE' });
}

async function requestJson<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: { Accept: 'application/json', ...init.headers },
  });

  if (!response.ok) {
    throw new Error(await readErrorMessage(response));
  }

  return response.json() as Promise<T>;
}

async function readErrorMessage(response: Response): Promise<string> {
  try {
    const body = await response.json() as { message?: string };
    return body.message ?? `Não foi possível concluir a operação (${response.status})`;
  } catch {
    return `Não foi possível concluir a operação (${response.status})`;
  }
}
