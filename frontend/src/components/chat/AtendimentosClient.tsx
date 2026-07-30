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
  AtendimentoLembrete,
  AtendimentoResumo,
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
};

export function AtendimentosClient({
  initialConversations,
  atendentes,
  user,
  initialAtendimentoId = null,
}: Props) {
  const [conversations, setConversations] = useState(initialConversations);
  const [activeId, setActiveId] = useState<number | null>(
    initialAtendimentoId ?? initialConversations[0]?.id ?? null,
  );
  const [detail, setDetail] = useState<AtendimentoDetalhe | null>(null);
  const [messages, setMessages] = useState<MensagemAtendimento[]>([]);
  const [quickMessages, setQuickMessages] = useState<MensagemRapida[]>([]);
  const [availableTags, setAvailableTags] = useState<TagOperacional[]>([]);
  const [activeTags, setActiveTags] = useState<TagOperacional[]>([]);
  const [reminders, setReminders] = useState<AtendimentoLembrete[]>([]);
  const [remindersLoading, setRemindersLoading] = useState(false);
  const [remindersError, setRemindersError] = useState<string | null>(null);
  const [filter, setFilter] = useState<AtendimentoFilter>('TODOS');
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
  const knownNotifications = useRef<Set<number> | null>(null);
  const activeIdRef = useRef<number | null>(activeId);
  const listAbortController = useRef<AbortController | null>(null);
  const listRequestVersion = useRef(0);
  const activeAbortController = useRef<AbortController | null>(null);
  const activeRequestVersion = useRef(0);
  const activeInFlight = useRef(false);
  const bloquearSelecaoAutomatica = useRef(false);
  const firstListEffect = useRef(true);
  const reopenDetailsButton = useRef<HTMLButtonElement>(null);
  const focusReopenDetails = useRef(false);
  const debouncedSearch = useDebouncedValue(search, 300);
  const searchKey = isSearchableTerm(debouncedSearch)
    ? normalizeSearchText(debouncedSearch)
    : '';
  const requestSearchRef = useRef('');
  requestSearchRef.current = searchKey ? debouncedSearch.trim() : '';
  const canManage = user.perfil === 'GESTOR' || user.perfil === 'RECEPCIONISTA';

  const atualizarSelecao = useCallback((nextId: number | null) => {
    activeAbortController.current?.abort();
    activeRequestVersion.current += 1;
    activeInFlight.current = false;
    activeIdRef.current = nextId;
    if (nextId !== null) bloquearSelecaoAutomatica.current = false;
    setActiveId(nextId);
    window.history.replaceState({}, '', nextId ? `/atendimentos?atendimentoId=${nextId}` : '/atendimentos');
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

  const refreshList = useCallback(async () => {
    listAbortController.current?.abort();
    const controller = new AbortController();
    const requestVersion = ++listRequestVersion.current;
    listAbortController.current = controller;
    setSearching(true);
    try {
      const page = await listAtendimentos(
        { filtro: filter, tipo: type, busca: requestSearchRef.current },
        controller.signal,
      );
      if (controller.signal.aborted || requestVersion !== listRequestVersion.current) return;
      setConversations(page.content);
      setListError(null);
      if (activeIdRef.current === null && !bloquearSelecaoAutomatica.current && page.content[0]) {
        atualizarSelecao(page.content[0].id);
      }
    } catch (cause) {
      if (controller.signal.aborted || requestVersion !== listRequestVersion.current) return;
      setListError(errorMessage(cause));
    } finally {
      if (!controller.signal.aborted && requestVersion === listRequestVersion.current) {
        setSearching(false);
      }
    }
  }, [atualizarSelecao, filter, searchKey, type]);

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
      setMessages(nextMessages);
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
      setDetailLoading(false);
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
    atualizarSelecao(id);
    void loadActiveConversation(id, 'select');
    void refreshList();

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
    if (filter === 'FINALIZADOS') {
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

  async function encerrarAtendimentoSelecionado() {
    const atendimentoId = activeIdRef.current;
    if (!atendimentoId || busy) return;
    setBusy(true);
    setError(null);
    try {
      const encerrado = await encerrarAtendimento(atendimentoId);
      setCloseIndividualDialogOpen(false);
      aplicarEncerramentoIndividual(atendimentoId, encerrado);
      setFeedback('Atendimento encerrado.');
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setBusy(false);
    }
  }

  async function encerrarTodosAtendimentosAtivos() {
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      const resultado = await encerrarTodosAtendimentos({ confirmado: true });
      setCloseAllDialogOpen(false);
      limparAtendimentoEncerrado();
      setComposerDrafts({});
      if (filter !== 'FINALIZADOS') setConversations([]);
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
        filter={filter}
        type={type}
        search={search}
        searching={searching}
        error={listError}
        onRetry={() => void refreshList()}
        onSelect={atualizarSelecao}
        onFilterChange={(nextFilter, nextType) => {
          bloquearSelecaoAutomatica.current = false;
          setFilter(nextFilter);
          setType(nextType);
        }}
        onSearchChange={setSearch}
        canStartManual={canManage}
        onStartManual={() => setStartDialogOpen(true)}
        canCloseAll={canManage}
        closeAllLoading={closeAllLoading}
        onCloseAll={() => void solicitarEncerramentoTodos()}
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
          onDraftChange={(content) => {
            if (!activeId) return;
            setComposerDrafts((current) => ({ ...current, [activeId]: content }));
          }}
          onSend={(content) => activeId
            ? runAction(() => enviarMensagem(activeId, content), { propagate: true, targetId: activeId })
            : Promise.resolve()}
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
        onConfirm={() => void encerrarAtendimentoSelecionado()}
      />
      <EncerrarAtendimentoDialog
        open={closeAllDialogOpen}
        mode="MASSA"
        total={closeAllTotal}
        processing={busy}
        onOpenChange={setCloseAllDialogOpen}
        onConfirm={() => void encerrarTodosAtendimentosAtivos()}
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
