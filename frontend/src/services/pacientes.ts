import type { TagOperacional } from '@/types/operacional';

export function adicionarTagPaciente(id: number, tagId: number): Promise<TagOperacional[]> {
  return requestJson(`/api/pacientes/${id}/tags/${tagId}`, { method: 'POST' });
}

export function removerTagPaciente(id: number, tagId: number): Promise<void> {
  return requestVoid(`/api/pacientes/${id}/tags/${tagId}`, { method: 'DELETE' });
}

async function requestJson<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set('Accept', 'application/json');
  if (init.body && !(init.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }
  const response = await fetch(path, { ...init, headers, cache: 'no-store' });
  if (!response.ok) throw new Error(await readError(response));
  return response.json() as Promise<T>;
}

async function requestVoid(path: string, init: RequestInit): Promise<void> {
  const response = await fetch(path, { ...init, cache: 'no-store' });
  if (!response.ok) throw new Error(await readError(response));
}

async function readError(response: Response): Promise<string> {
  try {
    const body = await response.json() as { message?: string };
    return body.message ?? `Falha na operacao (${response.status})`;
  } catch {
    return `Falha na operacao (${response.status})`;
  }
}
