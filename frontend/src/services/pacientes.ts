import type { TagOperacional } from '@/types/operacional';
import type {
  ImportacaoCsvContatoMapping,
  ImportacaoCsvContatoPreview,
  ImportacaoCsvContatoResultado,
  PacientePage,
} from '@/types/paciente';

export function pesquisarPacientes(params: {
  q?: string;
  page?: number;
  size?: number;
  status?: string;
  tag?: number;
}, signal?: AbortSignal): Promise<PacientePage> {
  const search = new URLSearchParams({
    page: String(params.page ?? 0),
    size: String(params.size ?? 25),
  });
  if (params.q?.trim()) search.set('q', params.q.trim());
  if (params.status) search.set('status', params.status);
  if (params.tag) search.set('tag', String(params.tag));
  return requestJson(`/api/pacientes/pesquisa?${search.toString()}`, { signal });
}

export function adicionarTagPaciente(id: number, tagId: number): Promise<TagOperacional[]> {
  return requestJson(`/api/pacientes/${id}/tags/${tagId}`, { method: 'POST' });
}

export function removerTagPaciente(id: number, tagId: number): Promise<void> {
  return requestVoid(`/api/pacientes/${id}/tags/${tagId}`, { method: 'DELETE' });
}

export function previewImportacaoCsv(
  file: File,
  mapping?: ImportacaoCsvContatoMapping,
): Promise<ImportacaoCsvContatoPreview> {
  const data = new FormData();
  data.append('file', file);
  if (mapping) data.append('mapping', new Blob([JSON.stringify(mapping)], { type: 'application/json' }));
  return requestJson('/api/pacientes/importacoes/csv/preview', { method: 'POST', body: data });
}

export function confirmarImportacaoCsv(
  file: File,
  expectedFileHash: string,
  mapping: ImportacaoCsvContatoMapping,
): Promise<ImportacaoCsvContatoResultado> {
  const data = new FormData();
  data.append('file', file);
  data.append('expectedFileHash', expectedFileHash);
  data.append('mapping', new Blob([JSON.stringify(mapping)], { type: 'application/json' }));
  return requestJson('/api/pacientes/importacoes/csv', { method: 'POST', body: data });
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
