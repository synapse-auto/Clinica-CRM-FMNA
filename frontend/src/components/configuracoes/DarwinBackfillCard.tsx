'use client';

import { useEffect, useRef, useState } from 'react';
import { AlertTriangle, Database, Loader2, RefreshCw, XCircle } from 'lucide-react';
import { DemoCard } from '@/components/demo/DemoCard';
import { StatusBadge } from '@/components/demo/StatusBadge';
import { cancelarBackfill, getBackfillStatus, getDarwinStatus, iniciarBackfill } from '@/services/darwinBackfill';
import type { DarwinBackfillStatus, DarwinStatus } from '@/types/darwin';

const POLL_INTERVAL_MS = 5000;

export function DarwinBackfillCard() {
  const [darwinStatus, setDarwinStatus] = useState<DarwinStatus | null>(null);
  const [backfillStatus, setBackfillStatus] = useState<DarwinBackfillStatus | null>(null);
  const [canManage, setCanManage] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    let active = true;
    getDarwinStatus()
      .then((status) => { if (active) setDarwinStatus(status); })
      .catch(() => { if (active) setDarwinStatus(null); });
    getBackfillStatus()
      .then((status) => { if (active) setBackfillStatus(status); })
      .catch(() => { if (active) setCanManage(false); });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    if (backfillStatus?.status !== 'RUNNING') {
      if (intervalRef.current) { clearInterval(intervalRef.current); intervalRef.current = null; }
      return;
    }
    intervalRef.current = setInterval(() => {
      getBackfillStatus().then(setBackfillStatus).catch(() => {});
    }, POLL_INTERVAL_MS);
    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
    };
  }, [backfillStatus?.status]);

  async function handleStart() {
    setBusy(true);
    setError(null);
    try {
      setBackfillStatus(await iniciarBackfill());
      setConfirming(false);
    } catch (caughtError) {
      setError(friendlyMessage(caughtError, 'Não foi possível iniciar o backfill.'));
    } finally {
      setBusy(false);
    }
  }

  async function handleCancel() {
    setBusy(true);
    setError(null);
    try {
      setBackfillStatus(await cancelarBackfill());
    } catch (caughtError) {
      setError(friendlyMessage(caughtError, 'Não foi possível cancelar o backfill.'));
    } finally {
      setBusy(false);
    }
  }

  if (!darwinStatus || darwinStatus.provider !== 'DARWIN') return null;

  if (!canManage) {
    return (
      <DemoCard title="Backfill Darwin" description="Sincronização manual do espelho local de agendamentos" icon={<Database className="h-5 w-5" />}>
        <p className="px-4 pb-4 text-xs font-semibold text-clinic-muted">
          Apenas usuários administrativos internos podem iniciar ou acompanhar o backfill Darwin.
        </p>
      </DemoCard>
    );
  }

  const running = backfillStatus?.status === 'RUNNING';
  const successCount = backfillStatus ? backfillStatus.processados - backfillStatus.comErro : 0;

  return (
    <DemoCard
      title="Backfill Darwin"
      description="Sincronização manual do espelho local de agendamentos"
      icon={<Database className="h-5 w-5" />}
      actions={backfillStatus ? <StatusBadge tone={backfillTone(backfillStatus.status)}>{backfillStatus.status}</StatusBadge> : null}
    >
      <div className="space-y-3 px-4 pb-4">
        {error ? (
          <p role="alert" className="rounded-lg border border-clinic-danger/30 bg-clinic-danger/10 px-3 py-2 text-xs font-semibold text-clinic-danger">
            {error}
          </p>
        ) : null}

        <p className="flex items-start gap-1.5 rounded-lg bg-clinic-surface-muted px-3 py-2 text-[11px] leading-5 text-clinic-muted">
          <AlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0 text-clinic-warning" />
          A Darwin não expõe listagem completa da agenda — a cobertura é limitada aos pacientes já
          conhecidos pelo CRM ({darwinStatus.coverage}). O progresso do backfill fica em memória e é
          perdido se o backend reiniciar.
        </p>

        {backfillStatus ? (
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
            <MetricBox label="Total" value={backfillStatus.totalPacientes} />
            <MetricBox label="Processados" value={backfillStatus.processados} />
            <MetricBox label="Sucesso" value={successCount} />
            <MetricBox label="Falhas" value={backfillStatus.comErro} />
          </div>
        ) : null}

        <div className="flex flex-wrap items-center gap-2">
          {running ? (
            <button
              type="button"
              onClick={handleCancel}
              disabled={busy}
              className="flex items-center gap-1.5 rounded-lg border border-clinic-danger/30 bg-clinic-danger/10 px-3 py-2 text-[10px] font-bold text-clinic-danger disabled:opacity-50"
            >
              {busy ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <XCircle className="h-3.5 w-3.5" />}
              Cancelar backfill
            </button>
          ) : confirming ? (
            <>
              <span className="text-[10px] font-semibold text-clinic-text">Confirma iniciar o backfill agora?</span>
              <button
                type="button"
                onClick={handleStart}
                disabled={busy}
                className="rounded-lg bg-clinic-primary px-3 py-2 text-[10px] font-bold text-white disabled:opacity-50"
              >
                {busy ? 'Iniciando...' : 'Confirmar início'}
              </button>
              <button
                type="button"
                onClick={() => setConfirming(false)}
                disabled={busy}
                className="rounded-lg border border-clinic-border bg-clinic-surface px-3 py-2 text-[10px] font-bold text-clinic-text"
              >
                Voltar
              </button>
            </>
          ) : (
            <button
              type="button"
              onClick={() => setConfirming(true)}
              className="flex items-center gap-1.5 rounded-lg bg-clinic-primary px-3 py-2 text-[10px] font-bold text-white"
            >
              <RefreshCw className="h-3.5 w-3.5" />
              Iniciar backfill
            </button>
          )}
        </div>
      </div>
    </DemoCard>
  );
}

function MetricBox({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-lg border border-clinic-border bg-clinic-surface-muted px-3 py-2 text-center">
      <p className="text-lg font-extrabold text-clinic-text">{value}</p>
      <p className="text-[9px] font-bold uppercase text-clinic-muted">{label}</p>
    </div>
  );
}

function backfillTone(status: string): 'green' | 'orange' | 'slate' | 'teal' {
  if (status === 'CONCLUIDO') return 'green';
  if (status === 'ERRO' || status === 'CANCELADO') return 'orange';
  if (status === 'RUNNING') return 'teal';
  return 'slate';
}

function friendlyMessage(caughtError: unknown, fallback: string): string {
  return caughtError instanceof Error && caughtError.message ? caughtError.message : fallback;
}
