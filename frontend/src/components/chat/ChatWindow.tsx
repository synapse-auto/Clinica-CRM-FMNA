'use client';

import Image from 'next/image';
import { useCallback, useEffect, useRef, useState, type KeyboardEvent } from 'react';
import {
  AlertCircle,
  Check,
  CheckCheck,
  Clock3,
  FileText,
  MessageSquareText,
  Paperclip,
  Send,
  Search,
} from 'lucide-react';
import type {
  AtendimentoDetalhe,
  MensagemAtendimento,
} from '@/types/atendimento';
import type { MensagemRapida } from '@/types/operacional';
import { ContactAvatar } from './ContactAvatar';

type Props = {
  detail: AtendimentoDetalhe | null;
  messages: MensagemAtendimento[];
  quickMessages: MensagemRapida[];
  busy: boolean;
  error: string | null;
  onSend: (content: string) => Promise<void>;
  onAttach: (file: File) => Promise<void>;
};

export function ChatWindow({ detail, messages, quickMessages, busy, error, onSend, onAttach }: Props) {
  const [content, setContent] = useState('');
  const [quickOpen, setQuickOpen] = useState(false);
  const [quickSearch, setQuickSearch] = useState('');
  const [showNewMessagesNotice, setShowNewMessagesNotice] = useState(false);
  const fileInput = useRef<HTMLInputElement>(null);
  const composer = useRef<HTMLTextAreaElement>(null);
  const messageScrollContainer = useRef<HTMLDivElement>(null);
  const messageEnd = useRef<HTMLDivElement>(null);
  const previousConversationId = useRef<number | null>(null);
  const previousLastMessageId = useRef<number | null>(null);
  const isNearBottom = useRef(true);
  const filteredQuickMessages = filterQuickMessages(quickMessages, quickSearch);
  const lastMessage = messages.at(-1);

  const scrollToLastMessage = useCallback((behavior: ScrollBehavior = 'auto') => {
    window.requestAnimationFrame(() => {
      const container = messageScrollContainer.current;
      if (!container) return;
      container.scrollTop = container.scrollHeight;
      messageEnd.current?.scrollIntoView?.({ behavior, block: 'end' });
      isNearBottom.current = true;
      setShowNewMessagesNotice(false);
    });
  }, []);

  function handleMessageScroll() {
    const container = messageScrollContainer.current;
    if (!container) return;
    const distanceFromBottom = container.scrollHeight - container.scrollTop - container.clientHeight;
    const nextIsNearBottom = distanceFromBottom <= 96;
    isNearBottom.current = nextIsNearBottom;
    if (nextIsNearBottom) setShowNewMessagesNotice(false);
  }

  useEffect(() => {
    const currentConversationId = detail?.id ?? null;
    const currentLastMessageId = lastMessage?.id ?? null;
    const conversationChanged = previousConversationId.current !== currentConversationId;
    const messageChanged = previousLastMessageId.current !== currentLastMessageId;

    if (!currentConversationId) {
      previousConversationId.current = null;
      previousLastMessageId.current = null;
      setShowNewMessagesNotice(false);
      return;
    }

    if (conversationChanged) {
      scrollToLastMessage('auto');
    } else if (messageChanged && currentLastMessageId) {
      if (isNearBottom.current || lastMessage?.direcao === 'SAIDA') {
        scrollToLastMessage(lastMessage?.direcao === 'SAIDA' ? 'smooth' : 'auto');
      } else {
        setShowNewMessagesNotice(true);
      }
    }

    previousConversationId.current = currentConversationId;
    previousLastMessageId.current = currentLastMessageId;
  }, [detail?.id, lastMessage?.direcao, lastMessage?.id, scrollToLastMessage]);

  useEffect(() => {
    const input = composer.current;
    if (!input) return;
    input.style.height = 'auto';
    input.style.height = `${Math.min(input.scrollHeight, 132)}px`;
  }, [content]);

  async function submit() {
    const value = content.trim();
    if (!value || busy || !detail) return;
    setContent('');
    await onSend(value);
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
          className="h-full overflow-y-auto px-6 py-7 custom-scrollbar"
          data-testid="message-scroll-container"
          onScroll={handleMessageScroll}
        >
          <div className="mx-auto flex w-full max-w-[880px] flex-col gap-5">
        {detail && messages.length === 0 ? (
          <p className="text-center text-[11px] text-clinic-muted">
            Ainda não há mensagens nesta conversa.
          </p>
        ) : messages.map((message) => (
          <MessageBubble key={message.id} message={message} />
        ))}
          <div ref={messageEnd} aria-hidden="true" data-testid="message-scroll-end" />
          </div>
        </div>
        {showNewMessagesNotice ? (
          <button
            type="button"
            aria-label="Ir para novas mensagens"
            className="absolute bottom-4 left-1/2 -translate-x-1/2 rounded-full border border-clinic-border bg-clinic-surface px-3 py-1.5 text-[10px] font-extrabold text-clinic-primary shadow-lg transition hover:bg-clinic-hover"
            onClick={() => scrollToLastMessage('smooth')}
          >
            Novas mensagens
          </button>
        ) : null}
      </div>

      <div className="shrink-0 border-t border-clinic-border bg-clinic-surface px-6 py-4">
        <input
          ref={fileInput}
          type="file"
          className="hidden"
          accept="image/jpeg,image/png,image/webp,audio/ogg,audio/mpeg,audio/mp4,application/pdf"
          onChange={(event) => {
            const file = event.target.files?.[0];
            if (file) void onAttach(file);
            event.target.value = '';
          }}
        />
        {quickOpen && detail ? (
          <div className="mx-auto mb-3 max-w-[880px] rounded-xl border border-clinic-border bg-clinic-surface p-3 shadow-lg shadow-clinic-primary/5">
            <label className="relative block">
              <Search className="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-clinic-muted" />
              <input
                value={quickSearch}
                onChange={(event) => setQuickSearch(event.target.value)}
                placeholder="Buscar mensagem rápida"
                aria-label="Buscar mensagens rápidas"
                className="h-9 w-full rounded-lg border border-clinic-border bg-clinic-input pl-8 pr-3 text-[11px] text-clinic-text outline-none focus:border-clinic-primary"
              />
            </label>
            <div className="mt-2 max-h-52 space-y-1 overflow-y-auto custom-scrollbar">
              {filteredQuickMessages.length === 0 ? (
                <p className="px-2 py-3 text-center text-[10px] text-clinic-muted">
                  Nenhuma mensagem rápida ativa.
                </p>
              ) : filteredQuickMessages.map((message) => (
                <button
                  key={message.id}
                  type="button"
                  onClick={() => {
                    setContent(message.conteudo);
                    setQuickOpen(false);
                    setQuickSearch('');
                  }}
                  className="w-full rounded-lg px-3 py-2.5 text-left hover:bg-clinic-hover"
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
        <div className="mx-auto flex w-full max-w-[880px] items-center gap-2.5">
          <button
            type="button"
            aria-label="Mensagens rápidas"
            disabled={!detail || busy || quickMessages.length === 0}
            onClick={() => setQuickOpen((current) => !current)}
            className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-clinic-muted transition hover:bg-clinic-hover hover:text-clinic-primary disabled:opacity-40"
          >
            <MessageSquareText className="h-4 w-4" />
          </button>
          <button
            type="button"
            aria-label="Anexar"
            disabled={!detail || busy}
            onClick={() => fileInput.current?.click()}
            className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-clinic-muted transition hover:bg-clinic-hover hover:text-clinic-primary disabled:opacity-40"
          >
            <Paperclip className="h-4 w-4 -rotate-45" />
          </button>
          <textarea
            ref={composer}
            value={content}
            disabled={!detail || busy}
            onChange={(event) => setContent(event.target.value)}
            onKeyDown={handleComposerKeyDown}
            placeholder="Digite uma mensagem..."
            rows={1}
            className="min-h-11 max-h-[132px] flex-1 resize-none rounded-xl border border-clinic-border bg-clinic-input px-4 py-3 text-[12px] text-clinic-text outline-none placeholder:text-clinic-muted focus:border-clinic-primary focus:ring-4 focus:ring-clinic-primary/10 disabled:opacity-50 custom-scrollbar"
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
    </section>
  );
}

function filterQuickMessages(messages: MensagemRapida[], search: string) {
  const term = search.trim().toLocaleLowerCase('pt-BR');
  return messages
    .filter((message) => message.ativo)
    .filter((message) => {
      if (!term) return true;
      return [
        message.titulo,
        message.atalho,
        message.conteudo,
      ].some((value) => value.toLocaleLowerCase('pt-BR').includes(term));
    });
}

function findQuickMessageShortcut(messages: MensagemRapida[], content: string) {
  const normalizedContent = normalizeShortcut(content);
  if (!normalizedContent) return null;
  return messages.find((message) => message.ativo && normalizeShortcut(message.atalho) === normalizedContent) ?? null;
}

function normalizeShortcut(value: string) {
  return value.trim().replace(/\s+/g, ' ').toLocaleLowerCase('pt-BR');
}

function MessageBubble({ message }: { message: MensagemAtendimento }) {
  const outbound = message.direcao === 'SAIDA';
  const failed = message.whatsappStatus === 'FALHA';
  return (
    <div className={`flex flex-col ${outbound ? 'items-end' : 'items-start'}`}>
      <div
        className={`max-w-[78%] rounded-2xl px-4 py-3 text-[13px] leading-5 shadow-sm ${
          failed
            ? 'rounded-tr-sm border border-clinic-danger/40 bg-clinic-danger/10 text-clinic-text'
            : outbound
            ? 'rounded-tr-sm bg-clinic-primary text-white'
            : 'rounded-tl-sm border border-clinic-border bg-clinic-surface text-clinic-text'
        }`}
      >
        {message.midia ? <MediaContent message={message} /> : message.conteudo}
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

function MediaContent({ message }: { message: MensagemAtendimento }) {
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
          onError={() => setError(true)}
          className="max-h-56 w-auto rounded-lg object-contain"
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

function StatusIcon({ status }: { status: string | null }) {
  if (status === 'FALHA') return <AlertCircle className="h-3 w-3 text-clinic-danger" />;
  if (status === 'READ' || status === 'LIDA') return <CheckCheck className="h-3 w-3 text-clinic-cyan" />;
  if (status === 'DELIVERED' || status === 'ENTREGUE') return <CheckCheck className="h-3 w-3" />;
  if (status === 'ENVIADA') return <Check className="h-3 w-3" />;
  return <Clock3 className="h-3 w-3" />;
}
