import type {
  AtendenteOption,
  AtendimentoDetalhe,
  AtendimentoFilter,
  AtendimentoPage,
  AtendimentoResumo,
  MensagemAtendimento,
  NotificacaoAtendimento,
} from '@/types/atendimento';

export async function listAtendimentos(params: {
  filtro: AtendimentoFilter;
  tipo: 'TODOS' | 'IA' | 'HUMANO';
  busca?: string;
}): Promise<AtendimentoPage<AtendimentoResumo>> {
  const search = new URLSearchParams({
    filtro: params.filtro,
    tipo: params.tipo,
    size: '50',
  });
  if (params.busca?.trim()) search.set('busca', params.busca.trim());
  return requestJson(`/api/atendimentos?${search}`);
}

export function getAtendimento(id: number): Promise<AtendimentoDetalhe> {
  return requestJson(`/api/atendimentos/${id}`);
}

export async function getMensagens(id: number): Promise<MensagemAtendimento[]> {
  const page = await requestJson<AtendimentoPage<MensagemAtendimento>>(
    `/api/atendimentos/${id}/mensagens?size=100`,
  );
  return [...page.content].reverse();
}

export function getAtendentes(): Promise<AtendenteOption[]> {
  return requestJson('/api/atendimentos/atendentes');
}

export function enviarMensagem(id: number, conteudo: string): Promise<MensagemAtendimento> {
  return requestJson(`/api/atendimentos/${id}/mensagens`, {
    method: 'POST',
    body: JSON.stringify({ tipoMedia: 'TEXTO', conteudo }),
  });
}

export function enviarAnexo(id: number, arquivo: File): Promise<MensagemAtendimento> {
  const body = new FormData();
  body.append('arquivo', arquivo);
  return requestJson(`/api/atendimentos/${id}/uploads-midia`, {
    method: 'POST',
    body,
  });
}

export function marcarAtendimentoComoLido(id: number): Promise<void> {
  return requestVoid(`/api/atendimentos/${id}/leitura`, { method: 'PATCH' });
}

export function transferirAtendimento(
  id: number,
  novoAtendenteId: number,
): Promise<AtendimentoDetalhe> {
  return requestJson(`/api/atendimentos/${id}/transferir`, {
    method: 'POST',
    body: JSON.stringify({ novoAtendenteId, motivo: 'Transferência pela tela de atendimentos' }),
  });
}

export function assumirAtendimento(id: number): Promise<AtendimentoDetalhe> {
  return requestJson(`/api/atendimentos/${id}/assumir`, { method: 'POST' });
}

export function revisarConvenio(
  id: number,
  resultado: 'APROVADO' | 'RECUSADO' | 'PENDENTE',
): Promise<AtendimentoDetalhe> {
  return requestJson(`/api/atendimentos/${id}/convenio`, {
    method: 'PATCH',
    body: JSON.stringify({ resultado }),
  });
}

export async function getNotificacoes(): Promise<NotificacaoAtendimento[]> {
  const page = await requestJson<AtendimentoPage<NotificacaoAtendimento>>(
    '/api/atendimentos/notificacoes?somenteNaoLidas=true&size=20',
  );
  return page.content;
}

export async function getNotificacoesResumo(): Promise<number> {
  const resumo = await requestJson<{ naoLidas: number }>(
    '/api/atendimentos/notificacoes/resumo',
  );
  return resumo.naoLidas;
}

export function marcarNotificacoesComoLidas(): Promise<void> {
  return requestVoid('/api/atendimentos/notificacoes/leitura', { method: 'PATCH' });
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
    return body.message ?? `Falha na operação (${response.status})`;
  } catch {
    return `Falha na operação (${response.status})`;
  }
}
