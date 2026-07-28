'use client';

import { LoaderCircle, MessageCircle, X } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { iniciarAtendimento } from '@/services/atendimentos';
import type { IniciarAtendimentoResponse } from '@/types/atendimento';

type Props = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onStarted: (
    response: IniciarAtendimentoResponse,
    mensagemInicial: string,
  ) => Promise<void> | void;
};

export function IniciarAtendimentoDialog({
  open,
  onOpenChange,
  onStarted,
}: Props) {
  const [telefone, setTelefone] = useState('');
  const [mensagemInicial, setMensagemInicial] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const dialogRef = useRef<HTMLDivElement>(null);
  const phoneRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!open) return;
    phoneRef.current?.focus();
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !submitting) {
        onOpenChange(false);
        return;
      }
      if (event.key !== 'Tab') return;
      const focusable = dialogRef.current?.querySelectorAll<HTMLElement>(
        'button:not([disabled]), input:not([disabled]), textarea:not([disabled])',
      );
      if (!focusable?.length) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [onOpenChange, open, submitting]);

  if (!open) return null;

  const digits = telefone.replace(/\D/g, '');
  const phoneValid = digits.length >= 10 && digits.length <= 15;

  async function submit() {
    if (!phoneValid || submitting) return;
    setSubmitting(true);
    setError(null);
    try {
      const response = await iniciarAtendimento({ telefone });
      const nextMessage = mensagemInicial.trim();
      setTelefone('');
      setMensagemInicial('');
      onOpenChange(false);
      await onStarted(response, nextMessage);
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/45 p-4"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !submitting) onOpenChange(false);
      }}
    >
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="iniciar-atendimento-title"
        className="w-full max-w-lg rounded-lg border border-clinic-border bg-clinic-surface shadow-2xl"
      >
        <header className="flex items-start justify-between gap-3 border-b border-clinic-border px-5 py-4">
          <div className="flex gap-3">
            <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-clinic-primary/10 text-clinic-primary">
              <MessageCircle className="h-4 w-4" />
            </span>
            <div>
              <h2 id="iniciar-atendimento-title" className="text-[15px] font-extrabold text-clinic-text">
                Novo atendimento
              </h2>
              <p className="mt-0.5 text-[10px] text-clinic-muted">
                Abra uma conversa em modo humano pelo WhatsApp.
              </p>
            </div>
          </div>
          <button
            type="button"
            aria-label="Fechar novo atendimento"
            disabled={submitting}
            onClick={() => onOpenChange(false)}
            className="flex h-8 w-8 items-center justify-center rounded-lg text-clinic-muted hover:bg-clinic-hover hover:text-clinic-text disabled:opacity-50"
          >
            <X className="h-4 w-4" />
          </button>
        </header>

        <div className="space-y-4 p-5">
          <label className="block text-[11px] font-bold text-clinic-text">
            Telefone
            <input
              ref={phoneRef}
              type="tel"
              value={telefone}
              onChange={(event) => setTelefone(formatPhone(event.target.value))}
              onKeyDown={(event) => {
                if (event.key === 'Enter') event.preventDefault();
              }}
              aria-invalid={telefone.length > 0 && !phoneValid}
              placeholder="(83) 99999-9999"
              className="mt-1.5 h-10 w-full rounded-lg border border-clinic-border bg-clinic-input px-3 text-[12px] text-clinic-text outline-none focus:border-clinic-primary focus:ring-4 focus:ring-clinic-primary/10"
            />
            {telefone.length > 0 && !phoneValid ? (
              <span className="mt-1 block text-[10px] font-semibold text-clinic-danger">
                Informe DDD e número válidos.
              </span>
            ) : null}
          </label>

          <label className="block text-[11px] font-bold text-clinic-text">
            Primeira mensagem <span className="font-normal text-clinic-muted">(opcional)</span>
            <textarea
              value={mensagemInicial}
              onChange={(event) => setMensagemInicial(event.target.value)}
              rows={4}
              maxLength={4096}
              placeholder="Digite a mensagem que será enviada após abrir o atendimento."
              className="mt-1.5 w-full resize-y rounded-lg border border-clinic-border bg-clinic-input px-3 py-2 text-[12px] text-clinic-text outline-none focus:border-clinic-primary focus:ring-4 focus:ring-clinic-primary/10"
            />
          </label>

          <p className="rounded-lg bg-clinic-soft px-3 py-2 text-[10px] leading-4 text-clinic-muted">
            A mensagem será enviada após a abertura do atendimento. Dependendo do canal,
            pode ser necessário utilizar um template aprovado.
          </p>

          {error ? <p role="alert" className="text-[10px] font-semibold text-clinic-danger">{error}</p> : null}
        </div>

        <footer className="flex justify-end gap-2 border-t border-clinic-border px-5 py-4">
          <button
            type="button"
            disabled={submitting}
            onClick={() => onOpenChange(false)}
            className="h-9 rounded-lg border border-clinic-border px-4 text-[11px] font-bold text-clinic-text hover:bg-clinic-hover disabled:opacity-50"
          >
            Cancelar
          </button>
          <button
            type="button"
            disabled={!phoneValid || submitting}
            onClick={() => void submit()}
            className="inline-flex h-9 items-center gap-2 rounded-lg bg-clinic-primary px-4 text-[11px] font-extrabold text-white hover:brightness-95 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {submitting ? <LoaderCircle className="h-4 w-4 animate-spin" /> : <MessageCircle className="h-4 w-4" />}
            Iniciar atendimento
          </button>
        </footer>
      </div>
    </div>
  );
}

function formatPhone(value: string) {
  const hasPlus = value.trimStart().startsWith('+');
  const digits = value.replace(/\D/g, '').slice(0, 15);
  if (hasPlus) return `+${digits}`;
  if (digits.length <= 2) return digits;
  if (digits.length <= 7) return `(${digits.slice(0, 2)}) ${digits.slice(2)}`;
  if (digits.length <= 10) {
    return `(${digits.slice(0, 2)}) ${digits.slice(2, 6)}-${digits.slice(6)}`;
  }
  if (digits.length === 11) {
    return `(${digits.slice(0, 2)}) ${digits.slice(2, 7)}-${digits.slice(7)}`;
  }
  return digits;
}

function errorMessage(cause: unknown) {
  return cause instanceof Error ? cause.message : 'Não foi possível iniciar o atendimento.';
}
