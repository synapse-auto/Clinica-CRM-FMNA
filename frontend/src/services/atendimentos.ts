import type {
  AtendenteOption,
  AtendimentoDetalhe,
  AtendimentoFilter,
  AtendimentoLembrete,
  AtendimentoPage,
  AtendimentoResumo,
  MensagemAtendimento,
  NovoAtendimentoLembrete,
  NotificacaoAtendimento,
} from '@/types/atendimento';
import type { MensagemRapida, TagOperacional } from '@/types/operacional';

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

export async function getMensagensRapidasAtivas(): Promise<MensagemRapida[]> {
  const mensagens = await requestJson<MensagemRapida[]>('/api/mensagens-rapidas');
  return mensagens
    .filter((mensagem) => mensagem.ativo)
    .sort((a, b) => a.titulo.localeCompare(b.titulo, 'pt-BR'));
}

export async function getTagsOperacionaisAtivas(): Promise<TagOperacional[]> {
  const tags = await requestJson<TagOperacional[]>('/api/tags');
  return tags
    .filter((tag) => tag.ativo)
    .sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR'));
}

export function getAtendimentoTags(id: number): Promise<TagOperacional[]> {
  return requestJson(`/api/atendimentos/${id}/tags`);
}

export function adicionarTagAtendimento(id: number, tagId: number): Promise<TagOperacional[]> {
  return requestJson(`/api/atendimentos/${id}/tags/${tagId}`, { method: 'POST' });
}

export function removerTagAtendimento(id: number, tagId: number): Promise<void> {
  return requestVoid(`/api/atendimentos/${id}/tags/${tagId}`, { method: 'DELETE' });
}

export function getAtendimentoLembretes(id: number): Promise<AtendimentoLembrete[]> {
  return requestJson(`/api/atendimentos/${id}/lembretes`);
}

export function criarAtendimentoLembrete(
  id: number,
  lembrete: NovoAtendimentoLembrete,
): Promise<AtendimentoLembrete> {
  return requestJson(`/api/atendimentos/${id}/lembretes`, {
    method: 'POST',
    body: JSON.stringify(lembrete),
  });
}

export function concluirAtendimentoLembrete(
  id: number,
  lembreteId: number,
): Promise<AtendimentoLembrete> {
  return requestJson(`/api/atendimentos/${id}/lembretes/${lembreteId}/concluir`, {
    method: 'PATCH',
  });
}

export function cancelarAtendimentoLembrete(
  id: number,
  lembreteId: number,
): Promise<AtendimentoLembrete> {
  return requestJson(`/api/atendimentos/${id}/lembretes/${lembreteId}/cancelar`, {
    method: 'PATCH',
  });
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

export function ativarIaAtendimento(id: number): Promise<AtendimentoDetalhe> {
  return requestJson(`/api/atendimentos/${id}/modo-ia`, { method: 'PATCH' });
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
