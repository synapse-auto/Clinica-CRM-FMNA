'use client';

import { type FormEvent, type ReactNode, useState } from 'react';
import { AlertCircle, Clock, Edit3, Plus, ToggleLeft, ToggleRight, Trash2, X } from 'lucide-react';
import { EmptyState } from '@/components/demo/EmptyState';
import { PageHeader } from '@/components/demo/PageHeader';
import type { AtendenteOption } from '@/types/atendimento';
import type { HorarioAtendente, HorarioAtendentePayload } from '@/types/operacional';
import { formatTime, getResponseMessage, isApiErrorBody, safeJson } from './client-helpers';

type HorariosClientProps = {
  initialSchedules: HorarioAtendente[];
  attendants: AtendenteOption[];
  initialError: string | null;
  canManage: boolean;
};

const weekdays = [
  { value: 0, label: 'Domingo' },
  { value: 1, label: 'Segunda-feira' },
  { value: 2, label: 'Terça-feira' },
  { value: 3, label: 'Quarta-feira' },
  { value: 4, label: 'Quinta-feira' },
  { value: 5, label: 'Sexta-feira' },
  { value: 6, label: 'Sábado' },
];

export function HorariosClient({
  initialSchedules,
  attendants,
  initialError,
  canManage,
}: HorariosClientProps) {
  const [schedules, setSchedules] = useState(initialSchedules);
  const [editing, setEditing] = useState<HorarioAtendente | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  function openCreate() {
    setEditing(null);
    setSubmitError(null);
    setIsModalOpen(true);
  }

  function openEdit(schedule: HorarioAtendente) {
    setEditing(schedule);
    setSubmitError(null);
    setIsModalOpen(true);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitError(null);
    setIsSubmitting(true);

    const form = new FormData(event.currentTarget);
    const payload: HorarioAtendentePayload = {
      usuarioId: Number(form.get('usuarioId')),
      diaSemana: Number(form.get('diaSemana')),
      horaInicio: String(form.get('horaInicio') ?? ''),
      horaFim: String(form.get('horaFim') ?? ''),
      ativo: form.get('ativo') === 'on',
    };

    try {
      const response = await fetch(editing ? `/api/horarios/${editing.id}` : '/api/horarios', {
        method: editing ? 'PUT' : 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      const body = await safeJson<HorarioAtendente>(response);

      if (!response.ok || !body || isApiErrorBody(body)) {
        setSubmitError(getResponseMessage(body, 'Não foi possível salvar o horário.'));
        return;
      }

      upsertSchedule(body);
      setIsModalOpen(false);
    } catch {
      setSubmitError('Serviço de horários indisponível. Tente novamente em instantes.');
    } finally {
      setIsSubmitting(false);
    }
  }

  async function toggleStatus(schedule: HorarioAtendente) {
    const response = await fetch(`/api/horarios/${schedule.id}/status`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ativo: !schedule.ativo }),
    });
    const body = await safeJson<HorarioAtendente>(response);
    if (response.ok && body && !isApiErrorBody(body)) {
      upsertSchedule(body);
    }
  }

  async function deleteSchedule(schedule: HorarioAtendente) {
    if (!window.confirm(`Excluir o horário de ${schedule.usuarioNome}?`)) return;
    const response = await fetch(`/api/horarios/${schedule.id}`, { method: 'DELETE' });
    if (response.ok) {
      setSchedules((current) => current.filter((item) => item.id !== schedule.id));
    }
  }

  function upsertSchedule(schedule: HorarioAtendente) {
    setSchedules((current) => {
      const exists = current.some((item) => item.id === schedule.id);
      const next = exists
        ? current.map((item) => (item.id === schedule.id ? schedule : item))
        : [...current, schedule];
      return next.sort((a, b) => (
        a.usuarioNome.localeCompare(b.usuarioNome, 'pt-BR')
        || a.diaSemana - b.diaSemana
        || a.horaInicio.localeCompare(b.horaInicio)
      ));
    });
  }

  return (
    <>
      <PageHeader
        icon={<Clock className="h-4 w-4" />}
        title="Horários"
        description={schedules.length === 1 ? '1 janela cadastrada' : `${schedules.length} janelas cadastradas`}
        actions={canManage ? (
          <button type="button" onClick={openCreate} className="flex h-8 items-center gap-2 rounded-lg bg-clinic-primary px-3 text-[10px] font-bold text-white">
            <Plus className="h-3.5 w-3.5" />
            Novo horário
          </button>
        ) : null}
      />

      {initialError ? <ErrorMessage message={initialError} /> : null}

      {schedules.length === 0 && !initialError ? (
        <EmptyState
          icon={Clock}
          title="Nenhum horário cadastrado"
          description="Cadastre janelas reais de atendimento para cada usuário operacional."
        />
      ) : (
        <div className="overflow-hidden rounded-xl border border-clinic-border bg-clinic-surface">
          {schedules.map((schedule) => (
            <article key={schedule.id} className="flex flex-col gap-3 border-b border-clinic-border px-4 py-3 last:border-b-0 md:flex-row md:items-center md:justify-between">
              <div>
                <div className="flex flex-wrap items-center gap-2">
                  <h2 className="text-[12px] font-extrabold text-clinic-text">{schedule.usuarioNome}</h2>
                  <StatusPill active={schedule.ativo} />
                </div>
                <p className="mt-1 text-[10px] font-semibold text-clinic-muted">
                  {weekdayLabel(schedule.diaSemana)} · <span>{formatTime(schedule.horaInicio)} - {formatTime(schedule.horaFim)}</span>
                </p>
              </div>

              {canManage ? (
                <div className="flex flex-wrap gap-2">
                  <IconButton label="Editar horário" onClick={() => openEdit(schedule)} icon={<Edit3 className="h-3.5 w-3.5" />} />
                  <IconButton
                    label={schedule.ativo ? 'Desativar horário' : 'Ativar horário'}
                    onClick={() => void toggleStatus(schedule)}
                    icon={schedule.ativo ? <ToggleRight className="h-3.5 w-3.5" /> : <ToggleLeft className="h-3.5 w-3.5" />}
                  />
                  <IconButton label="Excluir horário" onClick={() => void deleteSchedule(schedule)} icon={<Trash2 className="h-3.5 w-3.5" />} danger />
                </div>
              ) : null}
            </article>
          ))}
        </div>
      )}

      {isModalOpen ? (
        <HorarioDialog
          schedule={editing}
          attendants={attendants}
          error={submitError}
          isSubmitting={isSubmitting}
          onClose={() => setIsModalOpen(false)}
          onSubmit={handleSubmit}
        />
      ) : null}
    </>
  );
}

function HorarioDialog({
  schedule,
  attendants,
  error,
  isSubmitting,
  onClose,
  onSubmit,
}: {
  schedule: HorarioAtendente | null;
  attendants: AtendenteOption[];
  error: string | null;
  isSubmitting: boolean;
  onClose: () => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/35 p-4">
      <section role="dialog" aria-modal="true" aria-labelledby="horario-dialog-title" className="w-full max-w-lg rounded-2xl border border-clinic-border bg-clinic-surface p-5 shadow-xl">
        <DialogHeader id="horario-dialog-title" title={schedule ? 'Editar horário' : 'Novo horário'} onClose={onClose} />
        {error ? <ErrorMessage message={error} /> : null}
        <form onSubmit={onSubmit} className="space-y-3">
          <label className="block">
            <span className="mb-1.5 block text-[10px] font-bold text-clinic-text">Atendente</span>
            <select name="usuarioId" required defaultValue={schedule?.usuarioId ?? attendants[0]?.id ?? ''} className="h-10 w-full rounded-lg border border-clinic-border bg-clinic-input px-3 text-sm text-clinic-text outline-none focus:border-clinic-primary">
              {attendants.length === 0 ? <option value="">Nenhum atendente disponível</option> : null}
              {attendants.map((attendant) => (
                <option key={attendant.id} value={attendant.id}>{attendant.nome}</option>
              ))}
            </select>
          </label>

          <label className="block">
            <span className="mb-1.5 block text-[10px] font-bold text-clinic-text">Dia da semana</span>
            <select name="diaSemana" required defaultValue={schedule?.diaSemana ?? 1} className="h-10 w-full rounded-lg border border-clinic-border bg-clinic-input px-3 text-sm text-clinic-text outline-none focus:border-clinic-primary">
              {weekdays.map((day) => (
                <option key={day.value} value={day.value}>{day.label}</option>
              ))}
            </select>
          </label>

          <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
            <TimeField label="Hora início" name="horaInicio" defaultValue={formatTime(schedule?.horaInicio ?? '08:00')} />
            <TimeField label="Hora fim" name="horaFim" defaultValue={formatTime(schedule?.horaFim ?? '18:00')} />
          </div>

          <ActiveCheckbox defaultChecked={schedule?.ativo ?? true} />
          <DialogActions isSubmitting={isSubmitting} submitLabel="Salvar horário" onClose={onClose} />
        </form>
      </section>
    </div>
  );
}

function TimeField({ label, name, defaultValue }: { label: string; name: string; defaultValue: string }) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-[10px] font-bold text-clinic-text">{label}</span>
      <input name={name} type="time" required defaultValue={defaultValue} className="h-10 w-full rounded-lg border border-clinic-border bg-clinic-input px-3 text-sm text-clinic-text outline-none focus:border-clinic-primary" />
    </label>
  );
}

function ErrorMessage({ message }: { message: string }) {
  return (
    <p role="alert" className="mb-3 flex items-center gap-2 rounded-lg border border-clinic-danger/30 bg-clinic-danger/10 px-3 py-2 text-[10px] font-semibold text-clinic-danger">
      <AlertCircle className="h-3.5 w-3.5" />
      {message}
    </p>
  );
}

function DialogHeader({ id, title, onClose }: { id: string; title: string; onClose: () => void }) {
  return (
    <div className="mb-4 flex items-start justify-between gap-3">
      <h2 id={id} className="text-[15px] font-extrabold text-clinic-text">{title}</h2>
      <button type="button" onClick={onClose} className="flex h-8 w-8 items-center justify-center rounded-lg text-clinic-muted hover:bg-clinic-soft hover:text-clinic-text" aria-label="Fechar">
        <X className="h-4 w-4" />
      </button>
    </div>
  );
}

function ActiveCheckbox({ defaultChecked }: { defaultChecked: boolean }) {
  return (
    <label className="flex items-center gap-2 text-[10px] font-bold text-clinic-text">
      <input name="ativo" type="checkbox" defaultChecked={defaultChecked} className="h-4 w-4 accent-clinic-primary" />
      Ativo
    </label>
  );
}

function DialogActions({ isSubmitting, submitLabel, onClose }: { isSubmitting: boolean; submitLabel: string; onClose: () => void }) {
  return (
    <div className="flex flex-col-reverse gap-2 pt-2 sm:flex-row sm:justify-end">
      <button type="button" onClick={onClose} className="h-9 rounded-lg border border-clinic-border px-4 text-[10px] font-bold text-clinic-text hover:bg-clinic-soft">Cancelar</button>
      <button type="submit" disabled={isSubmitting} className="h-9 rounded-lg bg-clinic-primary px-4 text-[10px] font-bold text-white disabled:cursor-wait disabled:opacity-70">{isSubmitting ? 'Salvando...' : submitLabel}</button>
    </div>
  );
}

function StatusPill({ active }: { active: boolean }) {
  return (
    <span className={active ? 'rounded-full bg-clinic-primary/10 px-2 py-1 text-[8px] font-bold text-clinic-primary' : 'rounded-full bg-clinic-soft px-2 py-1 text-[8px] font-bold text-clinic-muted'}>
      {active ? 'Ativo' : 'Inativo'}
    </span>
  );
}

function IconButton({ label, icon, onClick, danger }: { label: string; icon: ReactNode; onClick: () => void; danger?: boolean }) {
  return (
    <button type="button" aria-label={label} title={label} onClick={onClick} className={`flex h-8 w-8 items-center justify-center rounded-lg border border-clinic-border ${danger ? 'text-clinic-danger hover:bg-clinic-danger/10' : 'text-clinic-muted hover:bg-clinic-soft hover:text-clinic-text'}`}>
      {icon}
    </button>
  );
}

function weekdayLabel(value: number) {
  return weekdays.find((day) => day.value === value)?.label ?? `Dia ${value}`;
}
