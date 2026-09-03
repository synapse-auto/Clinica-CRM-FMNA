'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { PanelRightOpen } from 'lucide-react';
import type { AuthUser } from '@/lib/auth/types';
import {
  adicionarTagAtendimento,
  assumirAtendimento,
  ativarIaAtendimento,
  cancelarAtendimentoLembrete,
  concluirAtendimentoLembrete,
  contarAtendimentosAtivos,
  criarAtendimentoLembrete,
  enviarAnexo,
  enviarMensagem,
  enviarWhatsappTemplate,
  encerrarAtendimento,
  encerrarTodosAtendimentos,
  getAtendimento,
  getAtendimentoLembretes,
  getAtendimentoTags,
  getMensagensRapidasAtivas,
  getMensagens,
  getNotificacoes,
  getNotificacoesResumo,
  getTagsOperacionaisAtivas,
  iniciarAtendimento,
  listAtendimentos,
  marcarAtendimentoComoLido,
  marcarNotificacoesComoLidas,
  removerTagAtendimento,
  revisarConvenio,
  transferirAtendimento,
  isWhatsappTemplateRequiredError,
} from '@/services/atendimentos';
import type {
  AtendenteOption,
  AtendimentoDetalhe,
  AtendimentoFilter,
  AtendimentoFiltroOperacional,
  AtendimentoLembrete,
  AtendimentoResumo,
  AtendimentoView,
  EncerramentoIndividualRequest,
  EnviarTemplateWhatsappRequest,
  MensagemAtendimento,
  NovoAtendimentoLembrete,
  IniciarAtendimentoResponse,
} from '@/types/atendimento';
import type { MensagemRapida, TagOperacional } from '@/types/operacional';
import { ChatList } from './ChatList';
import { ChatWindow } from './ChatWindow';
import { IniciarAtendimentoDialog } from './IniciarAtendimentoDialog';
import { ContactDetails } from './ContactDetails';
import { EncerrarAtendimentoDialog } from './EncerrarAtendimentoDialog';
import { useDebouncedValue } from '@/hooks/useDebouncedValue';
import { isSearchableTerm, normalizeSearchText } from '@/lib/search';

const DETAILS_PANEL_STORAGE_KEY = 'clinica-crm-atendimentos-details-open';

type TextQueueItem = {
  atendimentoId: number;
  clientId: number;
  conteudo: string;
};

function getAtendimentosUrl(view: AtendimentoView, atendimentoId: number | null) {
  const params = new URLSearchParams();
  if (view === 'FINALIZADOS') params.set('visao', 'finalizados');
  if (atendimentoId !== null) params.set('atendimentoId', String(atendimentoId));
  const query = params.toString();
  return query ? `/atendimentos?${query}` : '/atendimentos';
}

function isAbortError(cause: unknown): boolean {
  return cause instanceof DOMException
    ? cause.name === 'AbortError'
    : (cause as { name?: string } | null)?.name === 'AbortError';
}

type Props = {
  initialConversations: AtendimentoResumo[];
  atendentes: AtendenteOption[];
  user: AuthUser;
  initialAtendimentoId?: number | null;
  initialView?: AtendimentoView;
};

export function AtendimentosClient({
  initialConversations,
  atendentes,
  user,
  initialAtendimentoId = null,
  initialView = 'ATIVOS',
}: Props) {
  const [view, setView] = useState<AtendimentoView>(initialView);
  const [conversations, setConversations] = useState(
    initialView === 'ATIVOS' ? initialConversations : [],
  );
  const [activeId, setActiveId] = useState<number | null>(
    initialView === 'ATIVOS' ? initialAtendimentoId ?? initialConversations[0]?.id ?? null : null,
  );
  const [detail, setDetail] = useState<AtendimentoDetalhe | null>(null);
  const [messages, setMessages] = useState<MensagemAtendimento[]>([]);
  const [quickMessages, setQuickMessages] = useState<MensagemRapida[]>([]);
  const [availableTags, setAvailableTags] = useState<TagOperacional[]>([]);
  const [activeTags, setActiveTags] = useState<TagOperacional[]>([]);
  const [reminders, setReminders] = useState<AtendimentoLembrete[]>([]);
  const [remindersLoading, setRemindersLoading] = useState(false);
  const [remindersError, setRemindersError] = useState<string | null>(null);
  const [filter, setFilter] = useState<AtendimentoFiltroOperacional>('MEUS');
  const [type, setType] = useState<'TODOS' | 'IA' | 'HUMANO'>('TODOS');
  const [search, setSearch] = useState('');
  const [searching, setSearching] = useState(false);
  const [listError, setListError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [notificationCount, setNotificationCount] = useState(0);
  const [transferAlert, setTransferAlert] = useState<{ atendimentoId: number; descricao: string } | null>(null);
  const [detailsOpen, setDetailsOpen] = useState(false);
  const [startDialogOpen, setStartDialogOpen] = useState(false);
  const [closeIndividualDialogOpen, setCloseIndividualDialogOpen] = useState(false);
  const [closeAllDialogOpen, setCloseAllDialogOpen] = useState(false);
  const [closeAllTotal, setCloseAllTotal] = useState(0);
  const [closeAllLoading, setCloseAllLoading] = useState(false);
  const [feedback, setFeedback] = useState<string | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [composerDrafts, setComposerDrafts] = useState<Record<number, string>>({});
  const [outboxMessages, setOutboxMessages] = useState<Record<number, MensagemAtendimento[]>>({});
  const knownNotifications = useRef<Set<number> | null>(null);
  const closeAllActionFocus = useRef<(() => void) | null>(null);
  const activeIdRef = useRef<number | null>(activeId);
  const viewRef = useRef<AtendimentoView>(initialView);
  const initialRequestedIdRef = useRef<number | null>(
    initialView === 'FINALIZADOS' ? initialAtendimentoId : null,
  );
  const listAbortController = useRef<AbortController | null>(null);
  const listRequestVersion = useRef(0);
  const activeAbortController = useRef<AbortController | null>(null);
  const activeRequestVersion = useRef(0);
  const activeInFlight = useRef(false);
  const individualClosureInFlight = useRef(false);
  const bloquearSelecaoAutomatica = useRef(false);
  const viewTransitioning = useRef(initialView === 'FINALIZADOS');
  const firstListEffect = useRef(initialView === 'ATIVOS');
  const reopenDetailsButton = useRef<HTMLButtonElement>(null);
  const focusReopenDetails = useRef(false);
  const textQueues = useRef(new Map<number, TextQueueItem[]>());
  const drainingTextQueues = useRef(new Set<number>());
  const outboxMessagesRef = useRef<Record<number, MensagemAtendimento[]>>({});
  const failedTextContents = useRef(new Map<number, { atendimentoId: number; conteudo: string }>());
  const messageRenderKeys = useRef(new Map<number, string>());
  const nextClientMessageId = useRef(-1);
  const debouncedSearch = useDebouncedValue(search, 300);
  const searchKey = isSearchableTerm(debouncedSearch)
    ? normalizeSearchText(debouncedSearch)
    : '';
  const requestSearchRef = useRef('');
  requestSearchRef.current = searchKey ? debouncedSearch.trim() : '';
  const canManage = user.perfil === 'GESTOR' || user.perfil === 'RECEPCIONISTA';
  const filtroDaLista: AtendimentoFilter = view === 'FINALIZADOS' ? 'FINALIZADOS' : filter;
  const tipoDaLista: 'TODOS' | 'IA' | 'HUMANO' = view === 'FINALIZADOS' ? 'TODOS' : type;

  const registerCloseAllActionFocus = useCallback((focus: () => void) => {
    closeAllActionFocus.current = focus;
  }, []);

  const atualizarSelecao = useCallback((nextId: number | null, nextView = viewRef.current) => {
    activeAbortController.current?.abort();
    activeRequestVersion.current += 1;
    activeInFlight.current = false;
    activeIdRef.current = nextId;
    if (nextId !== null) bloquearSelecaoAutomatica.current = false;
    setActiveId(nextId);
    window.history.replaceState({}, '', getAtendimentosUrl(nextView, nextId));
  }, []);

  const limparAtendimentoEncerrado = useCallback(() => {
    bloquearSelecaoAutomatica.current = true;
    atualizarSelecao(null);
    setDetail(null);
    setMessages([]);
    setActiveTags([]);
    setReminders([]);
    setRemindersError(null);
    setError(null);
    setDetailLoading(false);
  }, [atualizarSelecao]);

  const mudarVisao = useCallback((nextView: AtendimentoView) => {
    if (nextView === viewRef.current) return;
    listAbortController.current?.abort();
    listRequestVersion.current += 1;
    activeAbortController.current?.abort();
    activeRequestVersion.current += 1;
    activeInFlight.current = false;
    bloquearSelecaoAutomatica.current = false;
    viewTransitioning.current = true;
    initialRequestedIdRef.current = null;
    viewRef.current = nextView;
    setConversations([]);
    setListError(null);
    atualizarSelecao(null, nextView);
    setDetail(null);
    setMessages([]);
    setActiveTags([]);
    setReminders([]);
    setRemindersError(null);
    setError(null);
    setDetailLoading(true);
    setView(nextView);
  }, [atualizarSelecao]);

  useEffect(() => {
    const stored = window.localStorage.getItem(DETAILS_PANEL_STORAGE_KEY);
    if (stored === 'true' || stored === 'false') {
      setDetailsOpen(stored === 'true');
      return;
    }
    setDetailsOpen(window.matchMedia?.('(min-width: 1600px)').matches ?? false);
  }, []);

  useEffect(() => {
    if (!detailsOpen && focusReopenDetails.current) {
      focusReopenDetails.current = false;
      reopenDetailsButton.current?.focus();
    }
  }, [detailsOpen]);

  function changeDetailsOpen(open: boolean) {
    focusReopenDetails.current = !open;
    setDetailsOpen(open);
    window.localStorage.setItem(DETAILS_PANEL_STORAGE_KEY, String(open));
  }

  function changeCloseAllDialogOpen(open: boolean) {
    setCloseAllDialogOpen(open);
    if (!open) window.requestAnimationFrame(() => closeAllActionFocus.current?.());
  }

  const refreshList = useCallback(async () => {
    listAbortController.current?.abort();
    const controller = new AbortController();
    const requestVersion = ++listRequestVersion.current;
    listAbortController.current = controller;
    setSearching(true);
    try {
      const page = await listAtendimentos(
        { filtro: filtroDaLista, tipo: tipoDaLista, busca: requestSearchRef.current },
        controller.signal,
      );
      if (controller.signal.aborted || requestVersion !== listRequestVersion.current) return;
      setConversations(page.content);
      setListError(null);
      const currentId = activeIdRef.current;
      const requestedId = initialRequestedIdRef.current;
      const currentStillVisible = currentId !== null && page.content.some((item) => item.id === currentId);
      const requestedStillVisible = requestedId !== null && page.content.some((item) => item.id === requestedId);
      initialRequestedIdRef.current = null;
      const nextId = currentStillVisible
        ? currentId
        : requestedStillVisible
          ? requestedId
          : (currentId !== null || !bloquearSelecaoAutomatica.current ? page.content[0]?.id ?? null : null);

      if (nextId !== currentId) {
        viewTransitioning.current = false;
        atualizarSelecao(nextId, viewRef.current);
      } else if (nextId === null) {
        viewTransitioning.current = false;
        setDetail(null);
        setMessages([]);
        setActiveTags([]);
        setReminders([]);
        setRemindersError(null);
        setError(null);
        setDetailLoading(false);
      }
    } catch (cause) {
      if (controller.signal.aborted || requestVersion !== listRequestVersion.current) return;
      setListError(errorMessage(cause));
    } finally {
      if (!controller.signal.aborted && requestVersion === listRequestVersion.current) {
        setSearching(false);
      }
    }
  }, [atualizarSelecao, filtroDaLista, searchKey, tipoDaLista]);

  const refreshListRef = useRef(refreshList);
  refreshListRef.current = refreshList;

  // Carrega uma conversa protegendo contra respostas obsoletas (AbortController + versão).
  // mode 'select': troca de conversa (limpa e mostra carregando); 'revalidate': atualização em segundo plano.
  const loadActiveConversation = useCallback(async (id: number, mode: 'select' | 'revalidate') => {
    activeAbortController.current?.abort();
    const controller = new AbortController();
    activeAbortController.current = controller;
    const version = ++activeRequestVersion.current;
    activeInFlight.current = true;
    const isCurrent = () => (
      version === activeRequestVersion.current
      && activeIdRef.current === id
      && !controller.signal.aborted
    );

    if (mode === 'select') setDetailLoading(true);

    // Secundários (tags e lembretes) carregam de forma independente — não bloqueiam as mensagens.
    void (async () => {
      try {
        const nextTags = await getAtendimentoTags(id, controller.signal);
        if (isCurrent()) setActiveTags(nextTags);
      } catch (cause) {
        if (!isAbortError(cause) && isCurrent()) setActiveTags([]);
      }
    })();
    void (async () => {
      if (isCurrent()) setRemindersLoading(true);
      try {
        const nextReminders = await getAtendimentoLembretes(id, controller.signal);
        if (isCurrent()) {
          setReminders(nextReminders);
          setRemindersError(null);
        }
      } catch (cause) {
        if (!isAbortError(cause) && isCurrent()) setRemindersError(errorMessage(cause));
      } finally {
        if (isCurrent()) setRemindersLoading(false);
      }
    })();

    // Crítico para abrir a conversa: detalhe + mensagens em paralelo, exibidos assim que prontos.
    try {
      const [nextDetail, nextMessages] = await Promise.all([
        getAtendimento(id, controller.signal),
        getMensagens(id, controller.signal),
      ]);
      if (!isCurrent()) return;
      setDetail(nextDetail);
      setMessages(mergeOutboxMessages(id, nextMessages));
      setError(null);
    } catch (cause) {
      if (isAbortError(cause) || !isCurrent()) return;
      setError(errorMessage(cause));
    } finally {
      if (version === activeRequestVersion.current) {
        activeInFlight.current = false;
        if (mode === 'select' && isCurrent()) setDetailLoading(false);
      }
    }
  }, []);
  const loadActiveConversationRef = useRef(loadActiveConversation);
  loadActiveConversationRef.current = loadActiveConversation;

  // Marcar como lido fora do caminho crítico: otimista na lista e reconciliado em segundo plano.
  const markAsReadInBackground = useCallback((id: number) => {
    setConversations((current) => current.map((item) => (
      item.id === id ? { ...item, naoLidas: 0 } : item
    )));
    void marcarAtendimentoComoLido(id)
      .then(() => refreshListRef.current())
      .catch(() => {
        // Falha ao marcar como lido não impede a conversa de abrir; reconcilia no próximo refresh.
      });
  }, []);

  useEffect(() => {
    activeIdRef.current = activeId;
  }, [activeId]);

  const refreshReminders = useCallback(async (id: number) => {
    setRemindersLoading(true);
    try {
      setReminders(await getAtendimentoLembretes(id));
      setRemindersError(null);
    } catch (cause) {
      setRemindersError(errorMessage(cause));
    } finally {
      setRemindersLoading(false);
    }
  }, []);

  useEffect(() => {
    async function loadOperationalData() {
      try {
        const [nextQuickMessages, nextTags] = await Promise.all([
          getMensagensRapidasAtivas(),
          getTagsOperacionaisAtivas(),
        ]);
        setQuickMessages(nextQuickMessages);
        setAvailableTags(nextTags);
      } catch (cause) {
        setError(errorMessage(cause));
      }
    }
    void loadOperationalData();
  }, []);

  useEffect(() => {
    if (firstListEffect.current) {
      firstListEffect.current = false;
      return;
    }
    void refreshList();
    return () => listAbortController.current?.abort();
  }, [refreshList]);

  useEffect(() => {
    if (!activeId) {
      activeAbortController.current?.abort();
      setDetail(null);
      setMessages([]);
      setActiveTags([]);
      setReminders([]);
      setRemindersError(null);
      setDetailLoading(viewTransitioning.current);
      return;
    }
    // Resposta imediata ao clique: descarta o conteúdo do paciente anterior e sinaliza carregamento.
    setDetail(null);
    setMessages([]);
    setActiveTags([]);
    setReminders([]);
    setRemindersError(null);
    setError(null);
    // Marcar como lido sai do caminho crítico (otimista + segundo plano).
    markAsReadInBackground(activeId);
    // Dados críticos começam imediatamente, sem esperar marcar como lido nem refresh da lista.
    void loadActiveConversation(activeId, 'select');
    return () => activeAbortController.current?.abort();
  }, [activeId, loadActiveConversation, markAsReadInBackground]);

  useEffect(() => {
    function revalidate() {
      if (document.hidden) return; // Aba oculta não gera trabalho de polling.
      void refreshListRef.current();
      const id = activeIdRef.current;
      // Clique do usuário tem prioridade: não revalida por cima de um carregamento em andamento.
      if (id && !activeInFlight.current) void loadActiveConversation(id, 'revalidate');
    }
    const interval = window.setInterval(revalidate, 5000);
    document.addEventListener('visibilitychange', revalidate);
    return () => {
      window.clearInterval(interval);
      document.removeEventListener('visibilitychange', revalidate);
    };
  }, [loadActiveConversation]);

  useEffect(() => {
    async function pollNotifications() {
      try {
        const [items, count] = await Promise.all([
          getNotificacoes(),
          getNotificacoesResumo(),
        ]);
        const ids = new Set(items.map((item) => item.id));
        if (knownNotifications.current) {
          const novas = items.filter((item) => !knownNotifications.current?.has(item.id));
          if (novas.length > 0) {
            setNotificationCount(count);
            const transferencia = novas.find((item) => (
              item.tipo === 'TRANSFERENCIA_IA' || item.tipo === 'ATENDIMENTO_ATRIBUIDO'
            ));
            if (transferencia) {
              setTransferAlert({
                atendimentoId: transferencia.atendimentoId,
                descricao: transferencia.descricao,
              });
              void refreshListRef.current();
              if (activeIdRef.current === transferencia.atendimentoId && !activeInFlight.current) {
                void loadActiveConversationRef.current(transferencia.atendimentoId, 'revalidate');
              }
            }
          }
        } else {
          setNotificationCount(count);
        }
        knownNotifications.current = ids;
        window.dispatchEvent(new CustomEvent('atendimentos:badge', { detail: count }));
      } catch {
        // O erro principal da tela continua reservado às operações do atendimento.
      }
    }
    void pollNotifications();
    const interval = window.setInterval(() => void pollNotifications(), 5000);
    return () => window.clearInterval(interval);
  }, []);

  function mergeOutboxMessages(atendimentoId: number, current: MensagemAtendimento[]) {
    return (outboxMessagesRef.current[atendimentoId] ?? []).reduce(mergeMensagem, current);
  }

  function updateOutboxMessages(
    atendimentoId: number,
    transform: (current: MensagemAtendimento[]) => MensagemAtendimento[],
  ) {
    const nextMessages = transform(outboxMessagesRef.current[atendimentoId] ?? []);
    const next = { ...outboxMessagesRef.current, [atendimentoId]: nextMessages };
    outboxMessagesRef.current = next;
    setOutboxMessages(next);
  }

  function createLocalTextMessage(item: TextQueueItem, whatsappStatus: string, motivoFalha: string | null = null): MensagemAtendimento {
    return {
      id: item.clientId,
      direcao: 'SAIDA',
      remetente: 'ATENDENTE',
      tipoMedia: 'TEXTO',
      conteudo: item.conteudo,
      conteudoPrevia: item.conteudo,
      whatsappStatus,
      motivoFalha,
      dataHora: new Date().toISOString(),
      entregueEm: null,
      lidaEm: null,
      midia: null,
      templateNome: null,
      templateIdioma: null,
    };
  }

  function removeOutboxMessage(atendimentoId: number, clientId: number) {
    updateOutboxMessages(atendimentoId, (current) => current.filter((message) => message.id !== clientId));
    if (activeIdRef.current === atendimentoId) {
      setMessages((current) => current.filter((message) => message.id !== clientId));
    }
  }

  function markQueuedMessageAsFailed(item: TextQueueItem, cause: unknown) {
    const failure = createLocalTextMessage(item, 'FALHA', errorMessage(cause));
    failedTextContents.current.set(item.clientId, { atendimentoId: item.atendimentoId, conteudo: item.conteudo });
    updateOutboxMessages(item.atendimentoId, (current) => current.map((message) => (
      message.id === item.clientId ? failure : message
    )));
    if (activeIdRef.current === item.atendimentoId) {
      setMessages((current) => current.map((message) => message.id === item.clientId ? failure : message));
      setError(errorMessage(cause));
    }
  }

  function resolvePendingMessage(atendimentoId: number, clientId: number, sentMessage: MensagemAtendimento) {
    updateOutboxMessages(atendimentoId, (current) => current.filter((message) => message.id !== clientId));
    messageRenderKeys.current.set(sentMessage.id, `local-${clientId}`);
    if (activeIdRef.current !== atendimentoId) return;
    setMessages((current) => {
      const pendingIndex = current.findIndex((message) => message.id === clientId);
      if (pendingIndex < 0) return mergeMensagem(current, sentMessage);
      return current.map((message) => message.id === clientId ? sentMessage : message);
    });
  }

  async function drainTextQueue(atendimentoId: number) {
    if (drainingTextQueues.current.has(atendimentoId)) return;
    drainingTextQueues.current.add(atendimentoId);
    try {
      const queue = textQueues.current.get(atendimentoId);
      while (queue?.length) {
        const item = queue.shift();
        if (!item) continue;
        try {
          const sentMessage = await enviarMensagem(item.atendimentoId, item.conteudo);
          resolvePendingMessage(item.atendimentoId, item.clientId, sentMessage);
          if (sentMessage.whatsappStatus === 'FALHA') {
            failedTextContents.current.set(sentMessage.id, { atendimentoId: item.atendimentoId, conteudo: item.conteudo });
            if (activeIdRef.current === item.atendimentoId) {
              setError(mensagemFalhaAmigavel(sentMessage.motivoFalha));
            }
          }
        } catch (cause) {
          markQueuedMessageAsFailed(item, cause);
          if (isWhatsappTemplateRequiredError(cause)) {
            while (queue.length) markQueuedMessageAsFailed(queue.shift()!, cause);
            void loadActiveConversationRef.current(atendimentoId, 'revalidate');
          }
        }
      }
    } finally {
      textQueues.current.delete(atendimentoId);
      drainingTextQueues.current.delete(atendimentoId);
      void refreshListRef.current();
    }
  }

  function enqueueTextMessage(atendimentoId: number, conteudo: string) {
    const item: TextQueueItem = {
      atendimentoId,
      conteudo,
      clientId: nextClientMessageId.current--,
    };
    updateOutboxMessages(atendimentoId, (current) => [...current, createLocalTextMessage(item, 'PENDENTE')]);
    if (activeIdRef.current === atendimentoId) {
      setMessages((current) => mergeMensagem(current, createLocalTextMessage(item, 'PENDENTE')));
    }
    const queue = textQueues.current.get(atendimentoId) ?? [];
    queue.push(item);
    textQueues.current.set(atendimentoId, queue);
    void drainTextQueue(atendimentoId);
  }

  function retryFailedTextMessage(messageId: number) {
    const failed = failedTextContents.current.get(messageId);
    if (!failed) return;
    failedTextContents.current.delete(messageId);
    if (messageId < 0) removeOutboxMessage(failed.atendimentoId, messageId);
    enqueueTextMessage(failed.atendimentoId, failed.conteudo);
  }

  async function runAction(
    action: () => Promise<unknown>,
    options: { propagate?: boolean; targetId?: number | null } = {},
  ) {
    const targetId = options.targetId ?? activeId;
    setBusy(true);
    try {
      const result = await action();
      const sentMessage = isMensagemAtendimento(result) ? result : null;
      if (sentMessage && targetId && activeIdRef.current === targetId) {
        setMessages((current) => mergeMensagem(current, sentMessage));
      }
      if (targetId && activeIdRef.current === targetId) await loadActiveConversation(targetId, 'revalidate');
      await refreshList();
      if (activeIdRef.current === targetId) {
        const failureMessage = sentMessage?.whatsappStatus === 'FALHA'
          ? mensagemFalhaAmigavel(
              sentMessage.motivoFalha,
              detail?.whatsappCapabilities?.enforcesCustomerCareWindow ?? true
            )
          : null;
        setError(failureMessage);
        if (failureMessage && options.propagate) {
          throw new Error(failureMessage);
        }
      }
    } catch (cause) {
      if (targetId && activeIdRef.current === targetId) {
        if (isWhatsappTemplateRequiredError(cause)) await loadActiveConversation(targetId, 'revalidate');
        setError(errorMessage(cause));
      }
      if (options.propagate) throw cause;
    } finally {
      setBusy(false);
    }
  }

  async function runReminderAction(action: () => Promise<unknown>) {
    if (!activeId) return;
    setBusy(true);
    try {
      await action();
      await refreshReminders(activeId);
      setError(null);
    } catch (cause) {
      setRemindersError(errorMessage(cause));
    } finally {
      setBusy(false);
    }
  }

  async function dismissNotifications() {
    try {
      await marcarNotificacoesComoLidas();
      setNotificationCount(0);
      window.dispatchEvent(new CustomEvent('atendimentos:badge', { detail: 0 }));
    } catch (cause) {
      setError(errorMessage(cause));
    }
  }

  async function handleManualStarted(
    response: IniciarAtendimentoResponse,
    mensagemInicial: string,
  ) {
    const id = response.atendimentoId;
    const mudouParaAtivos = viewRef.current !== 'ATIVOS';
    if (mudouParaAtivos) mudarVisao('ATIVOS');
    atualizarSelecao(id, 'ATIVOS');
    void loadActiveConversation(id, 'select');
    if (!mudouParaAtivos) void refreshList();

    if (!mensagemInicial) return;
    setBusy(true);
    try {
      const sentMessage = await enviarMensagem(id, mensagemInicial);
      if (activeIdRef.current === id) {
        setMessages((current) => mergeMensagem(current, sentMessage));
        setComposerDrafts((current) => ({
          ...current,
          [id]: sentMessage.whatsappStatus === 'FALHA' ? mensagemInicial : '',
        }));
        setError(sentMessage.whatsappStatus === 'FALHA'
          ? mensagemFalhaAmigavel(
              sentMessage.motivoFalha,
              response.atendimento.whatsappCapabilities?.enforcesCustomerCareWindow ?? true,
            )
          : null);
      }
    } catch (cause) {
      if (activeIdRef.current === id) {
        setComposerDrafts((current) => ({ ...current, [id]: mensagemInicial }));
        if (isWhatsappTemplateRequiredError(cause)) {
          await loadActiveConversation(id, 'revalidate');
        }
        setError(errorMessage(cause));
      }
    } finally {
      setBusy(false);
    }
  }

  async function solicitarEncerramentoTodos() {
    if (busy || closeAllLoading) return;
    setCloseAllLoading(true);
    setError(null);
    try {
      const { total } = await contarAtendimentosAtivos();
      if (total === 0) {
        setFeedback('Não há atendimentos ativos para encerrar.');
        return;
      }
      setCloseAllTotal(total);
      setCloseAllDialogOpen(true);
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setCloseAllLoading(false);
    }
  }

  function aplicarEncerramentoIndividual(atendimentoId: number, encerrado: AtendimentoDetalhe) {
    activeAbortController.current?.abort();
    activeRequestVersion.current += 1;
    activeInFlight.current = false;
    if (viewRef.current === 'FINALIZADOS') {
      setDetail(encerrado);
      void refreshList();
      return;
    }
    const indiceEncerrado = conversations.findIndex((item) => item.id === atendimentoId);
    const restantes = conversations.filter((item) => item.id !== atendimentoId);
    const indiceSeguinte = indiceEncerrado < 0 ? 0 : indiceEncerrado;
    const proximo = restantes[indiceSeguinte] ?? restantes[indiceSeguinte - 1] ?? null;
    setConversations(restantes);
    if (proximo) atualizarSelecao(proximo.id);
    else limparAtendimentoEncerrado();
    void refreshList();
  }

  async function encerrarAtendimentoSelecionado(confirmacao: string) {
    const atendimentoId = activeIdRef.current;
    if (confirmacao !== 'ENCERRAR' || !atendimentoId || busy || individualClosureInFlight.current) return;
    individualClosureInFlight.current = true;
    setBusy(true);
    setError(null);
    try {
      const request: EncerramentoIndividualRequest = {
        confirmado: true,
        origem: 'DIALOG_ATENDIMENTO',
        confirmacao: confirmacao as 'ENCERRAR',
      };
      const encerrado = await encerrarAtendimento(atendimentoId, request);
      setCloseIndividualDialogOpen(false);
      aplicarEncerramentoIndividual(atendimentoId, encerrado);
      setFeedback('Atendimento encerrado.');
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      individualClosureInFlight.current = false;
      setBusy(false);
    }
  }

  async function encerrarTodosAtendimentosAtivos(confirmacao: string) {
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      const resultado = await encerrarTodosAtendimentos({ confirmado: true, confirmacao });
      setCloseAllDialogOpen(false);
      limparAtendimentoEncerrado();
      setComposerDrafts({});
      if (viewRef.current === 'ATIVOS') setConversations([]);
      void refreshList();
      setFeedback(`${resultado.encerrados} atendimento${resultado.encerrados === 1 ? '' : 's'} encerrado${resultado.encerrados === 1 ? '' : 's'}.`);
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="relative flex h-full overflow-hidden bg-clinic-canvas">
      {notificationCount > 0 ? (
        <div className="absolute right-4 top-3 z-30 flex items-center gap-3 rounded-lg border border-clinic-primary/30 bg-clinic-surface px-3 py-2 text-[11px] font-semibold text-clinic-text shadow-lg">
          {notificationCount} notificação(ões) nova(s)
          <button className="text-clinic-primary" onClick={() => void dismissNotifications()}>
            Marcar como lidas
          </button>
        </div>
      ) : null}
      {feedback ? (
        <div role="status" className="absolute right-4 top-14 z-30 rounded-lg border border-clinic-primary/30 bg-clinic-surface px-3 py-2 text-[11px] font-semibold text-clinic-text shadow-lg">
          {feedback}
        </div>
      ) : null}
      {transferAlert ? (
        <div
          role="status"
          className="absolute right-4 top-16 z-30 flex max-w-sm items-center gap-3 rounded-lg border border-clinic-primary/30 bg-clinic-surface px-3 py-2 text-[11px] font-semibold text-clinic-text shadow-lg"
        >
          <span>{transferAlert.descricao || 'Um atendimento foi transferido para você.'}</span>
          <button
            type="button"
            className="shrink-0 text-clinic-primary"
            onClick={() => {
              atualizarSelecao(transferAlert.atendimentoId);
              setTransferAlert(null);
            }}
          >
            Abrir
          </button>
          <button
            type="button"
            aria-label="Fechar aviso de transferência"
            className="shrink-0 text-clinic-muted"
            onClick={() => setTransferAlert(null)}
          >
            Fechar
          </button>
        </div>
      ) : null}

      <ChatList
        conversations={conversations}
        activeId={activeId}
        view={view}
        filter={filter}
        type={type}
        search={search}
        searching={searching}
        error={listError}
        onRetry={() => void refreshList()}
        onSelect={atualizarSelecao}
        onViewChange={mudarVisao}
        onFilterChange={(nextFilter, nextType) => {
          bloquearSelecaoAutomatica.current = false;
          setFilter(nextFilter);
          setType(nextType);
        }}
        onSearchChange={setSearch}
        canStartManual={canManage}
        onStartManual={() => setStartDialogOpen(true)}
        canCloseAll={user.perfil === 'GESTOR' && view === 'ATIVOS'}
        closeAllLoading={closeAllLoading}
        onCloseAll={() => void solicitarEncerramentoTodos()}
        onCloseAllTriggerReady={registerCloseAllActionFocus}
      />
      <div className="flex min-w-0 flex-1">
        <ChatWindow
          detail={detail}
          loading={detailLoading}
          messages={messages}
          quickMessages={quickMessages}
          busy={busy}
          error={error}
          initialDraft={activeId ? composerDrafts[activeId] ?? '' : ''}
          pendingTextMessageCount={activeId
            ? (outboxMessages[activeId] ?? []).filter((message) => message.whatsappStatus === 'PENDENTE').length
            : 0}
          onDraftChange={(content) => {
            if (!activeId) return;
            setComposerDrafts((current) => ({ ...current, [activeId]: content }));
          }}
          onSend={(content) => {
            if (!activeId) return;
            const targetId = activeId;
            setComposerDrafts((current) => ({ ...current, [targetId]: '' }));
            enqueueTextMessage(targetId, content);
          }}
          onRetryFailedMessage={retryFailedTextMessage}
          canRetryFailedMessage={(messageId) => failedTextContents.current.has(messageId)}
          getMessageRenderKey={(message) => messageRenderKeys.current.get(message.id) ?? (message.id < 0 ? `local-${message.id}` : `message-${message.id}`)}
          onAttach={(file) => activeId
            ? runAction(() => enviarAnexo(activeId, file), { propagate: true, targetId: activeId })
            : Promise.resolve()}
          onSendTemplate={(request: EnviarTemplateWhatsappRequest) => activeId
            ? runAction(() => enviarWhatsappTemplate(activeId, request), { propagate: true, targetId: activeId })
            : Promise.resolve()}
        />
        {!detailsOpen ? (
          <div className="flex w-10 shrink-0 items-start justify-center border-l border-clinic-border bg-clinic-surface pt-3">
            <button
              ref={reopenDetailsButton}
              type="button"
              aria-label="Abrir detalhes do atendimento"
              aria-controls="atendimento-detalhes"
              aria-expanded="false"
              title="Abrir detalhes do atendimento"
              onClick={() => changeDetailsOpen(true)}
              className="flex h-8 w-8 items-center justify-center rounded-lg text-clinic-muted transition hover:bg-clinic-hover hover:text-clinic-text focus-visible:outline-2 focus-visible:outline-clinic-primary"
            >
              <PanelRightOpen className="h-4 w-4" />
            </button>
          </div>
        ) : null}
      </div>
      <div
        data-testid="contact-details-region"
        aria-hidden={!detailsOpen}
        inert={!detailsOpen}
        className={`shrink-0 overflow-hidden transition-[width,opacity] duration-150 ${detailsOpen ? 'w-[336px] opacity-100' : 'w-0 opacity-0 pointer-events-none'}`}
      >
        <ContactDetails
        detail={detail}
        loading={detailLoading}
        atendentes={atendentes}
        tags={activeTags}
        availableTags={availableTags}
        reminders={reminders}
        remindersLoading={remindersLoading}
        remindersError={remindersError}
        canManage={canManage}
        busy={busy}
        onClose={() => changeDetailsOpen(false)}
        onAssume={() => activeId
          ? runAction(() => assumirAtendimento(activeId))
          : Promise.resolve()}
        onEncerrarAtendimento={() => setCloseIndividualDialogOpen(true)}
        onActivateIa={() => activeId
          ? runAction(() => ativarIaAtendimento(activeId))
          : Promise.resolve()}
        onTransfer={(usuarioId) => activeId
          ? runAction(() => transferirAtendimento(activeId, usuarioId))
          : Promise.resolve()}
        onReview={(result) => activeId
          ? runAction(() => revisarConvenio(activeId, result))
          : Promise.resolve()}
        onAddTag={(tagId) => activeId
          ? runAction(() => adicionarTagAtendimento(activeId, tagId))
          : Promise.resolve()}
        onRemoveTag={(tagId) => activeId
          ? runAction(() => removerTagAtendimento(activeId, tagId))
          : Promise.resolve()}
        onCreateReminder={(lembrete: NovoAtendimentoLembrete) => activeId
          ? runReminderAction(() => criarAtendimentoLembrete(activeId, lembrete))
          : Promise.resolve()}
        onConcludeReminder={(lembreteId) => activeId
          ? runReminderAction(() => concluirAtendimentoLembrete(activeId, lembreteId))
          : Promise.resolve()}
        onCancelReminder={(lembreteId) => activeId
          ? runReminderAction(() => cancelarAtendimentoLembrete(activeId, lembreteId))
          : Promise.resolve()}
        />
      </div>
      <IniciarAtendimentoDialog
        open={startDialogOpen}
        onOpenChange={setStartDialogOpen}
        onStarted={handleManualStarted}
      />
      <EncerrarAtendimentoDialog
        open={closeIndividualDialogOpen}
        mode="INDIVIDUAL"
        processing={busy}
        onOpenChange={setCloseIndividualDialogOpen}
        onConfirm={(confirmacao) => void encerrarAtendimentoSelecionado(confirmacao ?? '')}
      />
      <EncerrarAtendimentoDialog
        open={closeAllDialogOpen}
        mode="MASSA"
        total={closeAllTotal}
        processing={busy}
        onOpenChange={changeCloseAllDialogOpen}
        onConfirm={(confirmacao) => void encerrarTodosAtendimentosAtivos(confirmacao ?? '')}
      />
    </div>
  );
}

function isMensagemAtendimento(value: unknown): value is MensagemAtendimento {
  return Boolean(value && typeof value === 'object'
    && 'id' in value && typeof value.id === 'number'
    && 'whatsappStatus' in value);
}

function mergeMensagem(current: MensagemAtendimento[], next: MensagemAtendimento) {
  const existingIndex = current.findIndex((message) => message.id === next.id);
  if (existingIndex < 0) return [...current, next];
  return current.map((message) => message.id === next.id ? next : message);
}

function mensagemFalhaAmigavel(reason: string | null, enforcesCustomerCareWindow = true) {
  const normalized = reason?.toLocaleLowerCase('pt-BR') ?? '';
  if (enforcesCustomerCareWindow && (normalized.includes('24h') || normalized.includes('template'))) {
    return 'Mensagem n\u00e3o enviada: a janela de atendimento de 24 horas foi encerrada pela Meta. Use um template aprovado ou aguarde uma nova mensagem do paciente.';
  }
  return reason ?? 'Mensagem n\u00e3o enviada pelo WhatsApp.';
}

function errorMessage(cause: unknown) {
  return cause instanceof Error ? cause.message : 'Não foi possível concluir a operação';
}
