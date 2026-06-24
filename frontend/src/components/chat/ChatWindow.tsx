'use client';

import Image from 'next/image';
import { useRef, useState } from 'react';
import {
  AlertCircle,
  Check,
  CheckCheck,
  Clock3,
  FileText,
  Paperclip,
  Send,
} from 'lucide-react';
import type {
  AtendimentoDetalhe,
  MensagemAtendimento,
} from '@/types/atendimento';

type Props = {
  detail: AtendimentoDetalhe | null;
  messages: MensagemAtendimento[];
  busy: boolean;
  error: string | null;
  onSend: (content: string) => Promise<void>;
  onAttach: (file: File) => Promise<void>;
};

export function ChatWindow({ detail, messages, busy, error, onSend, onAttach }: Props) {
  const [content, setContent] = useState('');
  const fileInput = useRef<HTMLInputElement>(null);

  async function submit() {
    const value = content.trim();
    if (!value || busy || !detail) return;
    setContent('');
    await onSend(value);
  }

  return (
    <section className="flex min-w-0 flex-1 flex-col bg-clinic-surface-muted">
      <header className="flex h-[58px] shrink-0 items-center border-b border-clinic-border bg-clinic-surface px-4">
        {detail ? (
          <div className="min-w-0">
            <h2 className="truncate text-[13px] font-extrabold text-clinic-text">
              {detail.paciente.nome}
            </h2>
            <p className="truncate text-[9px] text-clinic-muted">
              {detail.paciente.telefone} · {detail.tratadoPorIa ? 'Atendimento por IA' : (
                detail.atendentePrincipal
                  ? `Atendido por ${detail.atendentePrincipal.nome}`
                  : 'Aguardando atendente'
              )}
            </p>
          </div>
        ) : (
          <p className="text-[11px] text-clinic-muted">Selecione um atendimento</p>
        )}
      </header>

      {error ? (
        <div role="alert" className="flex items-center gap-2 border-b border-clinic-danger/30 bg-clinic-danger/10 px-4 py-2 text-[10px] font-semibold text-clinic-danger">
          <AlertCircle className="h-3.5 w-3.5" />
          {error}
        </div>
      ) : null}

      <div className="flex-1 space-y-4 overflow-y-auto p-5 custom-scrollbar">
        {detail && messages.length === 0 ? (
          <p className="text-center text-[11px] text-clinic-muted">
            Ainda não há mensagens nesta conversa.
          </p>
        ) : messages.map((message) => (
          <MessageBubble key={message.id} message={message} />
        ))}
      </div>

      <div className="shrink-0 border-t border-clinic-border bg-clinic-surface p-3">
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
        <div className="flex items-center gap-2">
          <button
            type="button"
            aria-label="Anexar"
            disabled={!detail || busy}
            onClick={() => fileInput.current?.click()}
            className="rounded-full p-2 text-clinic-muted transition hover:bg-clinic-hover hover:text-clinic-primary disabled:opacity-40"
          >
            <Paperclip className="h-4 w-4 -rotate-45" />
          </button>
          <input
            value={content}
            disabled={!detail || busy}
            onChange={(event) => setContent(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter' && !event.shiftKey) {
                event.preventDefault();
                void submit();
              }
            }}
            placeholder="Digite uma mensagem..."
            className="h-10 flex-1 rounded-full border border-clinic-border bg-clinic-input px-4 text-[11px] text-clinic-text outline-none placeholder:text-clinic-muted focus:border-clinic-primary disabled:opacity-50"
          />
          <button
            type="button"
            aria-label="Enviar"
            disabled={!detail || busy || !content.trim()}
            onClick={() => void submit()}
            className="flex h-10 w-10 items-center justify-center rounded-full bg-clinic-primary text-white transition hover:bg-clinic-primary-strong disabled:opacity-40"
          >
            <Send className="ml-0.5 h-4 w-4" />
          </button>
        </div>
      </div>
    </section>
  );
}

function MessageBubble({ message }: { message: MensagemAtendimento }) {
  const outbound = message.direcao === 'SAIDA';
  return (
    <div className={`flex flex-col ${outbound ? 'items-end' : 'items-start'}`}>
      <div
        className={`max-w-[72%] rounded-xl px-3.5 py-2.5 text-[11px] leading-5 shadow-sm ${
          outbound
            ? 'rounded-tr-sm bg-clinic-primary text-white'
            : 'rounded-tl-sm border border-clinic-border bg-clinic-surface text-clinic-text'
        }`}
      >
        {message.midia ? <MediaContent message={message} /> : message.conteudo}
      </div>
      <div className="mt-1 flex items-center gap-1 text-[8px] text-clinic-muted">
        {new Intl.DateTimeFormat('pt-BR', {
          hour: '2-digit',
          minute: '2-digit',
        }).format(new Date(message.dataHora))}
        {outbound ? <StatusIcon status={message.whatsappStatus} /> : null}
        {message.whatsappStatus === 'FALHA' ? (
          <span className="font-semibold text-clinic-danger">
            {message.motivoFalha ?? 'Falha no envio'}
          </span>
        ) : null}
      </div>
    </div>
  );
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
