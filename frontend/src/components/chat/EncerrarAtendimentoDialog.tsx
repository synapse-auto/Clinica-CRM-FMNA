'use client';

import { LoaderCircle, OctagonAlert, X } from 'lucide-react';

type Props = {
  open: boolean;
  mode: 'INDIVIDUAL' | 'MASSA';
  total?: number;
  processing: boolean;
  onOpenChange: (open: boolean) => void;
  onConfirm: () => void;
};

export function EncerrarAtendimentoDialog({
  open,
  mode,
  total = 0,
  processing,
  onOpenChange,
  onConfirm,
}: Props) {
  const isMass = mode === 'MASSA';

  if (!open) return null;

  const title = isMass ? 'ATENÇÃO: Encerrar todos os atendimentos?' : 'ATENÇÃO: Encerrar atendimento?';
  const description = isMass
    ? `Você está prestes a encerrar TODOS os ${total} atendimentos ativos desta clínica.`
    : 'Você está prestes a encerrar este atendimento. A conversa sairá da lista de ativos, mas todo o histórico será preservado em Finalizados.';
  const additionalDescription = isMass
    ? 'Esta ação removerá todos esses atendimentos da lista de ativos. As conversas e o histórico de mensagens serão preservados em Finalizados.'
    : null;
  const buttonLabel = isMass
    ? `Sim, encerrar todos os ${total} atendimentos`
    : 'Sim, encerrar atendimento';

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
              {additionalDescription ? <p className="mt-2 text-[11px] font-semibold leading-4 text-clinic-danger">{additionalDescription}</p> : null}
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

        <div className="p-5">
          <p className="text-[11px] font-semibold text-clinic-muted">
            Confirme somente clicando no botão de encerramento abaixo.
          </p>
        </div>

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
            disabled={processing}
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
