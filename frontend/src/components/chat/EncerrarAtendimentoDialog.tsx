'use client';

import { LoaderCircle, OctagonAlert, X } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';

type Props = {
  open: boolean;
  mode: 'INDIVIDUAL' | 'MASSA';
  total?: number;
  processing: boolean;
  onOpenChange: (open: boolean) => void;
  onConfirm: () => void;
};

const CONFIRMACAO_EM_MASSA = 'ENCERRAR TODOS';

export function EncerrarAtendimentoDialog({
  open,
  mode,
  total = 0,
  processing,
  onOpenChange,
  onConfirm,
}: Props) {
  const [confirmacao, setConfirmacao] = useState('');
  const confirmRef = useRef<HTMLInputElement>(null);
  const isMass = mode === 'MASSA';
  const confirmationValid = !isMass || confirmacao === CONFIRMACAO_EM_MASSA;

  useEffect(() => {
    if (!open) {
      setConfirmacao('');
      return;
    }
    if (isMass) window.requestAnimationFrame(() => confirmRef.current?.focus());
  }, [isMass, open]);

  if (!open) return null;

  const title = isMass ? 'Encerrar todos os atendimentos?' : 'Encerrar atendimento?';
  const description = isMass
    ? `Esta ação encerrará ${total} atendimento${total === 1 ? '' : 's'} ativo${total === 1 ? '' : 's'} desta clínica. Os contatos e históricos não serão excluídos e continuarão disponíveis em Finalizados.`
    : 'O atendimento sairá da lista ativa e continuará disponível em Finalizados. O contato e todo o histórico serão preservados.';
  const buttonLabel = isMass
    ? `Encerrar ${total} atendimento${total === 1 ? '' : 's'}`
    : 'Encerrar atendimento';

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/45 p-4"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !processing) onOpenChange(false);
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="encerrar-atendimento-title"
        className="w-full max-w-lg rounded-lg border border-clinic-danger/30 bg-clinic-surface shadow-2xl"
      >
        <header className="flex items-start justify-between gap-3 border-b border-clinic-border px-5 py-4">
          <div className="flex gap-3">
            <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-clinic-danger/10 text-clinic-danger">
              <OctagonAlert className="h-4 w-4" />
            </span>
            <div>
              <h2 id="encerrar-atendimento-title" className="text-[15px] font-extrabold text-clinic-text">
                {title}
              </h2>
              <p className="mt-1 text-[11px] leading-4 text-clinic-muted">{description}</p>
            </div>
          </div>
          <button
            type="button"
            aria-label="Fechar confirmação de encerramento"
            disabled={processing}
            onClick={() => onOpenChange(false)}
            className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-clinic-muted hover:bg-clinic-hover hover:text-clinic-text disabled:opacity-50"
          >
            <X className="h-4 w-4" />
          </button>
        </header>

        {isMass ? (
          <div className="space-y-2 p-5">
            <label className="block text-[11px] font-bold text-clinic-text">
              Para confirmar, digite <strong>{CONFIRMACAO_EM_MASSA}</strong>
              <input
                ref={confirmRef}
                value={confirmacao}
                disabled={processing}
                onChange={(event) => setConfirmacao(event.target.value)}
                aria-label="Confirmação para encerrar todos"
                className="mt-2 h-10 w-full rounded-lg border border-clinic-border bg-clinic-input px-3 text-[12px] text-clinic-text outline-none focus:border-clinic-danger focus:ring-4 focus:ring-clinic-danger/10 disabled:opacity-50"
              />
            </label>
          </div>
        ) : null}

        <footer className="flex justify-end gap-2 border-t border-clinic-border px-5 py-4">
          <button
            type="button"
            disabled={processing}
            onClick={() => onOpenChange(false)}
            className="h-9 rounded-lg border border-clinic-border px-4 text-[11px] font-bold text-clinic-text hover:bg-clinic-hover disabled:opacity-50"
          >
            Cancelar
          </button>
          <button
            type="button"
            disabled={!confirmationValid || processing}
            onClick={onConfirm}
            className="inline-flex h-9 items-center gap-2 rounded-lg bg-clinic-danger px-4 text-[11px] font-extrabold text-white hover:brightness-95 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {processing ? <LoaderCircle className="h-4 w-4 animate-spin" /> : <OctagonAlert className="h-4 w-4" />}
            {buttonLabel}
          </button>
        </footer>
      </div>
    </div>
  );
}
