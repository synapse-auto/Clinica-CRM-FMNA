import type {
  AtendenteOption,
  AtendimentoDetalhe,
  AtendimentoFilter,
  AtendimentoLembrete,
  AtendimentoPage,
  AtendimentoResumo,
  EnviarTemplateWhatsappRequest,
  MensagemAtendimento,
  NovoAtendimentoLembrete,
  NotificacaoAtendimento,
  WhatsappTemplate,
} from '@/types/atendimento';
import type { MensagemRapida, TagOperacional } from '@/types/operacional';

export async function listAtendimentos(params: {
  filtro: AtendimentoFilter;
  tipo: 'TODOS' | 'IA' | 'HUMANO';
  busca?: string;
}, signal?: AbortSignal): Promise<AtendimentoPage<AtendimentoResumo>> {
  const search = new URLSearchParams({
    filtro: params.filtro,
    tipo: params.tipo,
    size: '50',
  });
  if (params.busca?.trim()) search.set('busca', params.busca.trim());
  return requestJson(`/api/atendimentos?${search}`, { signal });
}

export function getAtendimento(id: number, signal?: AbortSignal): Promise<AtendimentoDetalhe> {
  return requestJson(`/api/atendimentos/${id}`, { signal });
}

export async function getMensagens(id: number, signal?: AbortSignal): Promise<MensagemAtendimento[]> {
  const page = await requestJson<AtendimentoPage<MensagemAtendimento>>(
    `/api/atendimentos/${id}/mensagens?size=100`,
    { signal },
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

export function getAtendimentoTags(id: number, signal?: AbortSignal): Promise<TagOperacional[]> {
  return requestJson(`/api/atendimentos/${id}/tags`, { signal });
}

export function adicionarTagAtendimento(id: number, tagId: number): Promise<TagOperacional[]> {
  return requestJson(`/api/atendimentos/${id}/tags/${tagId}`, { method: 'POST' });
}

export function removerTagAtendimento(id: number, tagId: number): Promise<void> {
  return requestVoid(`/api/atendimentos/${id}/tags/${tagId}`, { method: 'DELETE' });
}

export function getAtendimentoLembretes(id: number, signal?: AbortSignal): Promise<AtendimentoLembrete[]> {
  return requestJson(`/api/atendimentos/${id}/lembretes`, { signal });
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

export function getWhatsappTemplates(id: number): Promise<WhatsappTemplate[]> {
  return requestJson(`/api/atendimentos/${id}/templates-whatsapp`);
}

export function enviarWhatsappTemplate(
  id: number,
  request: EnviarTemplateWhatsappRequest,
): Promise<MensagemAtendimento> {
  return requestJson(`/api/atendimentos/${id}/mensagens-template`, {
    method: 'POST',
    body: JSON.stringify(request),
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
  if (!response.ok) throw await readError(response);
  return response.json() as Promise<T>;
}

async function requestVoid(path: string, init: RequestInit): Promise<void> {
  const response = await fetch(path, { ...init, cache: 'no-store' });
  if (!response.ok) throw await readError(response);
}

export class AtendimentoApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code: string | null = null,
  ) {
    super(message);
    this.name = 'AtendimentoApiError';
  }
}

export function isWhatsappTemplateRequiredError(cause: unknown): cause is AtendimentoApiError {
  return cause instanceof AtendimentoApiError
    && cause.code === 'WHATSAPP_TEMPLATE_REQUIRED';
}

async function readError(response: Response): Promise<AtendimentoApiError> {
  try {
    const body = await response.json() as { message?: string; code?: string };
    return new AtendimentoApiError(
      body.message ?? `Falha na operação (${response.status})`,
      response.status,
      body.code ?? null,
    );
  } catch {
    return new AtendimentoApiError(`Falha na operação (${response.status})`, response.status);
  }
}
