'use client';

import { Menu } from '@base-ui/react/menu';
import { useCallback, useEffect, useLayoutEffect, useRef, useState, type KeyboardEvent } from 'react';
import {
  AlertCircle,
  Check,
  CheckCheck,
  Clock3,
  FileUp,
  FileText,
  MessageSquareText,
  Plus,
  Send,
  Search,
} from 'lucide-react';
import type {
  AtendimentoDetalhe,
  EnviarTemplateWhatsappRequest,
  MensagemAtendimento,
} from '@/types/atendimento';
import type { MensagemRapida } from '@/types/operacional';
import { ContactAvatar } from './ContactAvatar';
import { WhatsappTemplateDialog } from './WhatsappTemplateDialog';
import { matchesSearchTokens } from '@/lib/search';

type Props = {
  detail: AtendimentoDetalhe | null;
  loading?: boolean;
  messages: MensagemAtendimento[];
  quickMessages: MensagemRapida[];
  busy: boolean;
  error: string | null;
  onSend: (content: string) => Promise<void>;
  onAttach: (file: File) => Promise<void>;
  onSendTemplate?: (request: EnviarTemplateWhatsappRequest) => Promise<void>;
};

const NEAR_BOTTOM_THRESHOLD = 96;
// Largura fluida do conteúdo do chat: ocupa toda a área útil e só limita em telas
// ultrawide (>1600px de coluna) para evitar linhas de leitura exageradas. Usada em
// mensagens, aviso de janela, seletor rápido e composer para manter alinhamento vertical.
const CHAT_CONTENT_WIDTH = 'mx-auto w-full max-w-[1600px]';

export function ChatWindow({ detail, loading = false, messages, quickMessages, busy, error, onSend, onAttach, onSendTemplate }: Props) {
  const [content, setContent] = useState('');
  const [quickOpen, setQuickOpen] = useState(false);
  const [addMenuOpen, setAddMenuOpen] = useState(false);
  const [templatesOpen, setTemplatesOpen] = useState(false);
  const [quickSearch, setQuickSearch] = useState('');
  const [quickActiveIndex, setQuickActiveIndex] = useState(0);
  const [showNewMessagesNotice, setShowNewMessagesNotice] = useState(false);
  const fileInput = useRef<HTMLInputElement>(null);
  const composer = useRef<HTMLTextAreaElement>(null);
  const addButton = useRef<HTMLButtonElement>(null);
  const templateOpener = useRef<HTMLElement | null>(null);
  const messageScrollContainer = useRef<HTMLDivElement>(null);
  const previousConversationId = useRef<number | null>(null);
  const previousMessageIds = useRef<number[]>([]);
  const previousScrollHeight = useRef(0);
  const previousScrollTop = useRef(0);
  const isNearBottom = useRef(true);
  const filteredQuickMessages = filterQuickMessages(quickMessages, quickSearch);
  const windowOpen = detail?.janelaWhatsappAberta !== false;
  const templatesAvailable = detail?.whatsappTemplatesDisponiveis === true;

  const scrollToLastMessage = useCallback((behavior: ScrollBehavior = 'auto') => {
    const container = messageScrollContainer.current;
    if (!container) return;
    if (behavior === 'smooth' && typeof container.scrollTo === 'function') {
      container.scrollTo({ top: container.scrollHeight, behavior });
    } else {
      container.scrollTop = container.scrollHeight;
    }
    previousScrollTop.current = container.scrollTop;
    previousScrollHeight.current = container.scrollHeight;
    isNearBottom.current = true;
    setShowNewMessagesNotice(false);
  }, []);

  function handleMessageScroll() {
    const container = messageScrollContainer.current;
    if (!container) return;
    const nextIsNearBottom = isContainerNearBottom(container);
    isNearBottom.current = nextIsNearBottom;
    previousScrollTop.current = container.scrollTop;
    previousScrollHeight.current = container.scrollHeight;
    if (nextIsNearBottom) setShowNewMessagesNotice(false);
  }

  useLayoutEffect(() => {
    const container = messageScrollContainer.current;
    const currentConversationId = detail?.id ?? null;
    const currentMessageIds = messages.map((message) => message.id);
    const conversationChanged = previousConversationId.current !== currentConversationId;

    if (!currentConversationId || !container) {
      previousConversationId.current = null;
      previousMessageIds.current = [];
      previousScrollHeight.current = 0;
      previousScrollTop.current = 0;
      isNearBottom.current = true;
      setShowNewMessagesNotice(false);
      return;
    }

    if (conversationChanged) {
      scrollToLastMessage('auto');
    } else {
      const previousIds = previousMessageIds.current;
      const prepended = isPrependedSequence(previousIds, currentMessageIds);
      const appended = isAppendedSequence(previousIds, currentMessageIds);

      if (prepended) {
        const addedHeight = Math.max(0, container.scrollHeight - previousScrollHeight.current);
        container.scrollTop = previousScrollTop.current + addedHeight;
        isNearBottom.current = isContainerNearBottom(container);
      } else if (appended) {
        const appendedMessages = messages.slice(previousIds.length);
        const ownMessageAppended = appendedMessages.some((message) => message.direcao === 'SAIDA');
        if (isNearBottom.current || ownMessageAppended) {
          scrollToLastMessage('auto');
        } else {
          setShowNewMessagesNotice(true);
        }
      } else if (sameSequence(previousIds, currentMessageIds)) {
        // Atualizações de status não alteram a posição de leitura.
      } else {
        isNearBottom.current = isContainerNearBottom(container);
      }
    }

    previousConversationId.current = currentConversationId;
    previousMessageIds.current = currentMessageIds;
    previousScrollTop.current = container.scrollTop;
    previousScrollHeight.current = container.scrollHeight;
  }, [detail?.id, messages, scrollToLastMessage]);

  const handleMediaLayoutChanged = useCallback((conversationId: number) => {
    if (previousConversationId.current !== conversationId) return;
    const container = messageScrollContainer.current;
    if (!container) return;
    if (isNearBottom.current) {
      scrollToLastMessage('auto');
      return;
    }
    previousScrollTop.current = container.scrollTop;
    previousScrollHeight.current = container.scrollHeight;
  }, [scrollToLastMessage]);

  useEffect(() => {
    function handleResize() {
      if (isNearBottom.current) scrollToLastMessage('auto');
    }
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, [scrollToLastMessage]);

  useEffect(() => {
    setContent('');
    setAddMenuOpen(false);
    setTemplatesOpen(false);
    closeQuickMessages();
  // O ID é a fronteira entre conversas; não carregamos estado sensível para outro paciente.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [detail?.id]);

  useEffect(() => {
    const input = composer.current;
    if (!input) return;
    input.style.height = 'auto';
    input.style.height = `${Math.min(input.scrollHeight, 132)}px`;
  }, [content]);

  useEffect(() => {
    setQuickActiveIndex((current) => Math.max(0, Math.min(current, filteredQuickMessages.length - 1)));
  }, [filteredQuickMessages.length]);

  function focusComposer() {
    window.requestAnimationFrame(() => {
      const input = composer.current;
      if (!input) return;
      input.focus();
      input.setSelectionRange(input.value.length, input.value.length);
    });
  }

  function closeQuickMessages({ focus = false }: { focus?: boolean } = {}) {
    setQuickOpen(false);
    setQuickSearch('');
    setQuickActiveIndex(0);
    if (focus) focusComposer();
  }

  function openQuickMessages() {
    setQuickOpen(true);
    setQuickSearch('');
    setQuickActiveIndex(0);
  }

  function selectQuickMessage(message: MensagemRapida) {
    setContent(message.conteudo);
    closeQuickMessages({ focus: true });
  }

  function handleQuickSearchKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.nativeEvent.isComposing) return;
    if (event.key === 'Escape') {
      event.preventDefault();
      closeQuickMessages({ focus: true });
      return;
    }
    if (event.key === 'Enter') {
      event.preventDefault();
      if (!event.shiftKey && filteredQuickMessages[quickActiveIndex]) {
        selectQuickMessage(filteredQuickMessages[quickActiveIndex]);
      }
      return;
    }
    if (event.key === 'ArrowDown' && filteredQuickMessages.length) {
      event.preventDefault();
      setQuickActiveIndex((current) => (current + 1) % filteredQuickMessages.length);
      return;
    }
    if (event.key === 'ArrowUp' && filteredQuickMessages.length) {
      event.preventDefault();
      setQuickActiveIndex((current) => (current - 1 + filteredQuickMessages.length) % filteredQuickMessages.length);
    }
  }

  async function submit() {
    const value = content.trim();
    if (!value || busy || !detail || !windowOpen) return;
    try {
      await onSend(value);
      setContent('');
    } catch {
      // O texto permanece para correção ou envio posterior por decisão do usuário.
    }
  }

  function openTemplates(opener: HTMLElement | null) {
    if (!detail || !templatesAvailable) return;
    templateOpener.current = opener;
    setAddMenuOpen(false);
    setTemplatesOpen(true);
  }

  function changeTemplatesOpen(nextOpen: boolean) {
    setTemplatesOpen(nextOpen);
    if (!nextOpen) {
      window.requestAnimationFrame(() => templateOpener.current?.focus());
    }
  }

  function handleComposerKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key !== 'Enter' || event.shiftKey || event.nativeEvent.isComposing) return;

    const quickMessage = findQuickMessageShortcut(quickMessages, content);
    if (quickMessage) {
      event.preventDefault();
      setContent(quickMessage.conteudo);
      window.requestAnimationFrame(() => {
        const input = composer.current;
        if (!input) return;
        input.focus();
        input.setSelectionRange(input.value.length, input.value.length);
      });
      return;
    }

    event.preventDefault();
    void submit();
  }

  return (
    <section className="flex min-w-0 flex-1 flex-col bg-clinic-canvas">
      <header className="flex min-h-[76px] shrink-0 items-center justify-between gap-4 border-b border-clinic-border bg-clinic-surface px-6">
        {detail ? (
          <div className="flex min-w-0 items-center gap-3">
            <ContactAvatar name={detail.paciente.nome} url={detail.paciente.fotoUrl} />
            <div className="min-w-0">
            <h2 className="truncate text-[15px] font-extrabold text-clinic-text">
              {detail.paciente.nome}
            </h2>
            <p className="mt-1 truncate text-[11px] text-clinic-muted">
              {detail.paciente.telefone} · {detail.tratadoPorIa ? 'Atendido por IA' : (
                detail.atendentePrincipal
                  ? `Atendido por ${detail.atendentePrincipal.nome}`
                  : 'Humano sem responsável'
              )}
            </p>
            </div>
            <span className="hidden rounded-full bg-clinic-blue/10 px-2.5 py-1 text-[10px] font-bold text-clinic-blue sm:inline-flex">
              {detail.tratadoPorIa ? 'IA ativa' : 'Humano'}
            </span>
          </div>
        ) : loading ? (
          <p className="text-[12px] text-clinic-muted" aria-live="polite">Carregando conversa…</p>
        ) : (
          <p className="text-[12px] text-clinic-muted">Selecione um atendimento</p>
        )}
      </header>

      {error ? (
        <div role="alert" className="flex items-center gap-2 border-b border-clinic-danger/30 bg-clinic-danger/10 px-6 py-2.5 text-[11px] font-semibold text-clinic-danger">
          <AlertCircle className="h-4 w-4" />
          {error}
        </div>
      ) : null}

      <div className="relative min-h-0 flex-1">
        <div
          ref={messageScrollContainer}
          className="h-full overflow-y-auto px-4 py-7 custom-scrollbar sm:px-6 lg:px-8"
          data-testid="message-scroll-container"
          onScroll={handleMessageScroll}
        >
          <div className={`${CHAT_CONTENT_WIDTH} flex flex-col gap-5`}>
        {loading && !detail ? (
          <p className="text-center text-[11px] text-clinic-muted" data-testid="chat-messages-loading" aria-live="polite">
            Carregando mensagens…
          </p>
        ) : detail && messages.length === 0 ? (
          <p className="text-center text-[11px] text-clinic-muted">
            Ainda não há mensagens nesta conversa.
          </p>
        ) : messages.map((message) => (
          <MessageBubble
            key={message.id}
            message={message}
            onMediaLayoutChanged={detail ? () => handleMediaLayoutChanged(detail.id) : undefined}
          />
        ))}
          <div aria-hidden="true" data-testid="message-scroll-end" />
          </div>
        </div>
        {showNewMessagesNotice ? (
          <button
            type="button"
            aria-label="Ir para o final da conversa"
            className="absolute bottom-4 left-1/2 -translate-x-1/2 rounded-full border border-clinic-border bg-clinic-surface px-3 py-1.5 text-[10px] font-extrabold text-clinic-primary shadow-lg transition hover:bg-clinic-hover"
            onClick={() => scrollToLastMessage('smooth')}
          >
            Novas mensagens
          </button>
        ) : null}
      </div>

      <div className="shrink-0 border-t border-clinic-border bg-clinic-surface px-4 py-4 sm:px-6 lg:px-8">
        <input
          ref={fileInput}
          type="file"
          className="hidden"
          accept="image/jpeg,image/png,image/webp,audio/ogg,audio/mpeg,audio/mp4,application/pdf"
          onChange={(event) => {
            const file = event.target.files?.[0];
            if (file && windowOpen) void onAttach(file).catch(() => undefined);
            event.target.value = '';
          }}
        />
        {windowOpen && quickOpen && detail ? (
          <div className={`${CHAT_CONTENT_WIDTH} mb-3 rounded-xl border border-clinic-border bg-clinic-surface p-3 shadow-lg shadow-clinic-primary/5`}>
            <label className="relative block">
              <Search className="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-clinic-muted" />
              <input
                value={quickSearch}
                onChange={(event) => {
                  setQuickSearch(event.target.value);
                  setQuickActiveIndex(0);
                }}
                onKeyDown={handleQuickSearchKeyDown}
                placeholder="Buscar mensagem rápida"
                aria-label="Buscar mensagens rápidas"
                role="combobox"
                aria-autocomplete="list"
                aria-expanded={quickOpen}
                aria-controls="quick-messages-list"
                aria-activedescendant={filteredQuickMessages[quickActiveIndex] ? `quick-message-${filteredQuickMessages[quickActiveIndex].id}` : undefined}
                className="h-9 w-full rounded-lg border border-clinic-border bg-clinic-input pl-8 pr-3 text-[11px] text-clinic-text outline-none focus:border-clinic-primary"
              />
            </label>
            <div id="quick-messages-list" role="listbox" className="mt-2 max-h-52 space-y-1 overflow-y-auto custom-scrollbar">
              {filteredQuickMessages.length === 0 ? (
                <p className="px-2 py-3 text-center text-[10px] text-clinic-muted">
                  Nenhuma mensagem rápida ativa.
                </p>
              ) : filteredQuickMessages.map((message, index) => (
                <button
                  key={message.id}
                  id={`quick-message-${message.id}`}
                  type="button"
                  role="option"
                  aria-selected={index === quickActiveIndex}
                  onMouseEnter={() => setQuickActiveIndex(index)}
                  onClick={() => selectQuickMessage(message)}
                  className={`w-full rounded-lg px-3 py-2.5 text-left hover:bg-clinic-hover ${index === quickActiveIndex ? 'bg-clinic-hover' : ''}`}
                >
                  <span className="block truncate text-[10px] font-extrabold text-clinic-text">
                    {message.titulo}
                  </span>
                  <span className="block truncate text-[9px] font-semibold text-clinic-primary">
                    {message.atalho}
                  </span>
                  <span className="block truncate text-[9px] text-clinic-muted">
                    {message.conteudo}
                  </span>
                </button>
              ))}
            </div>
          </div>
        ) : null}
        {detail && !windowOpen ? (
          <div className={`${CHAT_CONTENT_WIDTH} flex flex-col items-center rounded-lg border border-clinic-warning/35 bg-clinic-warning/10 px-4 py-5 text-center`}>
            <Clock3 className="h-5 w-5 text-clinic-warning" />
            <p className="mt-2 max-w-2xl text-xs font-bold text-clinic-text">
              {detail.aguardandoRespostaTemplate
                ? 'Template enviado. Aguardando uma resposta do paciente para liberar novas mensagens.'
                : 'A sessão de 24 horas para atendimento foi encerrada. A partir de agora, somente mensagens através de templates serão aceitas.'}
            </p>
            {!templatesAvailable ? (
              <p className="mt-2 text-[10px] text-clinic-danger">Templates da Meta não estão configurados para esta clínica.</p>
            ) : null}
            <button
              type="button"
              disabled={!templatesAvailable || busy}
              onClick={(event) => openTemplates(event.currentTarget)}
              className="mt-4 inline-flex h-10 items-center gap-2 rounded-md bg-clinic-primary px-4 text-xs font-extrabold text-white hover:bg-clinic-primary-strong disabled:opacity-40"
            >
              <MessageSquareText className="h-4 w-4" />
              Nova mensagem
            </button>
          </div>
        ) : (
          <div className={CHAT_CONTENT_WIDTH}>
            {detail ? <WhatsappWindowIndicator expiresAt={detail.janelaWhatsappExpiraEm} /> : null}
            <div className="mt-2 flex items-center gap-2.5">
              <button
                type="button"
                aria-label="Mensagens rápidas"
                disabled={!detail || busy || quickMessages.length === 0}
                onClick={() => (quickOpen ? closeQuickMessages() : openQuickMessages())}
                className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-clinic-muted transition hover:bg-clinic-hover hover:text-clinic-primary disabled:opacity-40"
              >
                <MessageSquareText className="h-4 w-4" />
              </button>
              <Menu.Root open={addMenuOpen} onOpenChange={setAddMenuOpen}>
                <Menu.Trigger
                  ref={addButton}
                  aria-label="Adicionar"
                  aria-expanded={addMenuOpen}
                  aria-haspopup="menu"
                  disabled={!detail || busy}
                  className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-clinic-muted transition hover:bg-clinic-hover hover:text-clinic-primary focus-visible:outline-2 focus-visible:outline-clinic-primary disabled:opacity-40"
                >
                  <Plus className="h-5 w-5" />
                </Menu.Trigger>
                <Menu.Portal>
                  <Menu.Positioner side="top" align="start" sideOffset={8} className="z-[70]">
                    <Menu.Popup className="w-64 rounded-md border border-clinic-border bg-clinic-surface p-1.5 text-clinic-text shadow-xl outline-none">
                      <Menu.Item
                        disabled={!windowOpen || busy}
                        onClick={() => fileInput.current?.click()}
                        className="flex cursor-default items-start gap-3 rounded-md px-3 py-2.5 text-xs outline-none data-[highlighted]:bg-clinic-hover data-[disabled]:opacity-45"
                      >
                        <FileUp className="mt-0.5 h-4 w-4 shrink-0" />
                        <span><span className="block font-bold">Enviar arquivo</span>{!windowOpen ? <span className="mt-0.5 block text-[10px] text-clinic-muted">Disponível somente dentro da janela de 24 horas.</span> : null}</span>
                      </Menu.Item>
                      <Menu.Item
                        disabled={!templatesAvailable || busy}
                        onClick={() => openTemplates(addButton.current)}
                        className="flex cursor-default items-start gap-3 rounded-md px-3 py-2.5 text-xs outline-none data-[highlighted]:bg-clinic-hover data-[disabled]:opacity-45"
                      >
                        <MessageSquareText className="mt-0.5 h-4 w-4 shrink-0" />
                        <span><span className="block font-bold">Templates</span>{!templatesAvailable ? <span className="mt-0.5 block text-[10px] text-clinic-muted">Templates da Meta não estão configurados para esta clínica.</span> : null}</span>
                      </Menu.Item>
                    </Menu.Popup>
                  </Menu.Positioner>
                </Menu.Portal>
              </Menu.Root>
              <textarea
                ref={composer}
                value={content}
                disabled={!detail || busy}
                onChange={(event) => setContent(event.target.value)}
                onKeyDown={handleComposerKeyDown}
                placeholder="Digite uma mensagem..."
                rows={1}
                className="min-h-11 max-h-[132px] min-w-0 flex-1 resize-none rounded-xl border border-clinic-border bg-clinic-input px-4 py-3 text-[12px] text-clinic-text outline-none placeholder:text-clinic-muted focus:border-clinic-primary focus:ring-4 focus:ring-clinic-primary/10 disabled:opacity-50 custom-scrollbar"
              />
              <button
                type="button"
                aria-label="Enviar"
                disabled={!detail || busy || !content.trim()}
                onClick={() => void submit()}
                className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-clinic-primary text-white transition hover:bg-clinic-primary-strong disabled:opacity-40"
              >
                <Send className="ml-0.5 h-4 w-4" />
              </button>
            </div>
          </div>
        )}
      </div>
      <WhatsappTemplateDialog
        open={templatesOpen}
        atendimentoId={detail?.id ?? null}
        onOpenChange={changeTemplatesOpen}
        onSend={onSendTemplate ?? (() => Promise.resolve())}
      />
    </section>
  );
}

function filterQuickMessages(messages: MensagemRapida[], search: string) {
  return messages
    .filter((message) => message.ativo)
    .filter((message) => matchesSearchTokens([
      message.titulo,
      message.atalho,
      message.conteudo,
    ], search));
}

function findQuickMessageShortcut(messages: MensagemRapida[], content: string) {
  const normalizedContent = normalizeShortcut(content);
  if (!normalizedContent) return null;
  return messages.find((message) => message.ativo && normalizeShortcut(message.atalho) === normalizedContent) ?? null;
}

function normalizeShortcut(value: string) {
  return value.trim().replace(/\s+/g, ' ').toLocaleLowerCase('pt-BR');
}

function WhatsappWindowIndicator({ expiresAt }: { expiresAt: string | null }) {
  const expiration = expiresAt ? new Date(expiresAt) : null;
  const validExpiration = expiration && !Number.isNaN(expiration.getTime()) ? expiration : null;
  const formatted = validExpiration ? new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(validExpiration) : null;
  const time = validExpiration && validExpiration.getTime() >= Date.now()
    ? new Intl.DateTimeFormat('pt-BR', { hour: '2-digit', minute: '2-digit' }).format(validExpiration)
    : null;
  return (
    <p
      title={formatted ? `Janela disponível até ${formatted}` : undefined}
      aria-label={formatted ? `Janela do WhatsApp aberta. Disponível até ${formatted}` : 'Janela do WhatsApp aberta'}
      className="flex items-center gap-1.5 text-[10px] font-semibold text-clinic-success"
    >
      <span className="h-1.5 w-1.5 rounded-full bg-clinic-success" />
      Janela do WhatsApp aberta{time ? ` · Disponível até ${time}` : ''}
    </p>
  );
}

function MessageBubble({
  message,
  onMediaLayoutChanged,
}: {
  message: MensagemAtendimento;
  onMediaLayoutChanged?: () => void;
}) {
  const outbound = message.direcao === 'SAIDA';
  const failed = message.whatsappStatus === 'FALHA';
  const template = message.tipoMedia === 'TEMPLATE';
  return (
    <div className={`flex flex-col ${outbound ? 'items-end' : 'items-start'}`}>
      <div
        className={`max-w-[min(88%,760px)] break-words rounded-2xl px-4 py-3 text-[13px] leading-5 shadow-sm ${
          failed
            ? 'rounded-tr-sm border border-clinic-danger/40 bg-clinic-danger/10 text-clinic-text'
            : outbound
            ? 'rounded-tr-sm bg-clinic-primary text-white'
            : 'rounded-tl-sm border border-clinic-border bg-clinic-surface text-clinic-text'
        }`}
      >
        {template ? (
          <div className="mb-2 flex flex-wrap items-center gap-1.5 text-[9px] font-bold">
            <span className={`rounded-full px-2 py-0.5 ${failed ? 'bg-clinic-danger/15 text-clinic-danger' : 'bg-white/15 text-current'}`}>Template</span>
            {message.templateNome ? <span className="opacity-85">{message.templateNome}</span> : null}
            {message.templateIdioma ? <span className="opacity-70">· {message.templateIdioma}</span> : null}
          </div>
        ) : null}
        <div className="whitespace-pre-wrap">{message.midia ? (
          <MediaContent message={message} onLayoutChanged={onMediaLayoutChanged} />
        ) : message.conteudo}</div>
      </div>
      <div className="mt-1 flex items-center gap-1 text-[10px] text-clinic-muted">
        {new Intl.DateTimeFormat('pt-BR', {
          hour: '2-digit',
          minute: '2-digit',
        }).format(new Date(message.dataHora))}
        {outbound ? <StatusIcon status={message.whatsappStatus} /> : null}
        {message.whatsappStatus === 'FALHA' ? (
          <span className="font-semibold text-clinic-danger">
            {mensagemFalhaAmigavel(message.motivoFalha)}
          </span>
        ) : null}
      </div>
    </div>
  );
}

function mensagemFalhaAmigavel(reason: string | null) {
  const normalized = reason?.toLocaleLowerCase('pt-BR') ?? '';
  if (normalized.includes('24h') || normalized.includes('template')) {
    return 'Mensagem n\u00e3o enviada. A janela de atendimento de 24 horas foi encerrada pela Meta. Use um template aprovado ou aguarde uma nova mensagem do paciente.';
  }
  return reason ?? 'Falha no envio';
}

function MediaContent({
  message,
  onLayoutChanged,
}: {
  message: MensagemAtendimento;
  onLayoutChanged?: () => void;
}) {
  const media = message.midia;
  const [error, setError] = useState(false);

  if (!media) return null;

  if (error) {
    const errorText = media.tipoMedia === 'IMAGEM'
      ? 'Imagem indisponível'
      : media.tipoMedia === 'AUDIO'
        ? 'Áudio indisponível'
        : 'Documento indisponível';
    return <span className="italic text-clinic-muted">{errorText}</span>;
  }

  if (media.tipoMedia === 'IMAGEM') {
    return (
      <a href={media.url} target="_blank" rel="noreferrer">
        <img
          src={media.url}
          alt={media.nomeArquivo ?? 'Imagem recebida'}
          onLoad={onLayoutChanged}
          onError={() => setError(true)}
          className="max-h-56 w-auto max-w-full rounded-lg object-contain"
        />
      </a>
    );
  }
  if (media.tipoMedia === 'AUDIO') {
    return (
      <audio
        controls
        preload="none"
        src={media.url}
        onLoadedMetadata={onLayoutChanged}
        onError={() => setError(true)}
        className="max-w-full"
      />
    );
  }
  return (
    <a
      href={media.url}
      target="_blank"
      rel="noreferrer"
      className="flex items-center gap-2 font-semibold underline"
    >
      <FileText className="h-4 w-4" />
      {media.nomeArquivo ?? 'Abrir documento'}
    </a>
  );
}

function isContainerNearBottom(container: HTMLElement) {
  return container.scrollHeight - container.scrollTop - container.clientHeight <= NEAR_BOTTOM_THRESHOLD;
}

function sameSequence(previousIds: number[], currentIds: number[]) {
  return previousIds.length === currentIds.length
    && previousIds.every((id, index) => id === currentIds[index]);
}

function isAppendedSequence(previousIds: number[], currentIds: number[]) {
  return currentIds.length > previousIds.length
    && previousIds.every((id, index) => id === currentIds[index]);
}

function isPrependedSequence(previousIds: number[], currentIds: number[]) {
  if (currentIds.length <= previousIds.length || previousIds.length === 0) return false;
  const offset = currentIds.length - previousIds.length;
  return previousIds.every((id, index) => id === currentIds[index + offset]);
}

function StatusIcon({ status }: { status: string | null }) {
  if (status === 'FALHA') return <AlertCircle className="h-3 w-3 text-clinic-danger" />;
  if (status === 'READ' || status === 'LIDA') return <CheckCheck className="h-3 w-3 text-clinic-cyan" />;
  if (status === 'DELIVERED' || status === 'ENTREGUE') return <CheckCheck className="h-3 w-3" />;
  if (status === 'ENVIADA') return <Check className="h-3 w-3" />;
  return <Clock3 className="h-3 w-3" />;
}
