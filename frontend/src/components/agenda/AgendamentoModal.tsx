'use client';

import { useState, type FormEvent } from 'react';
import { X } from 'lucide-react';
import type {
  Agendamento,
  AgendamentoPayload,
  AgendaOptions,
} from '@/types/agendamento';

type AgendamentoModalProps = {
  appointment: Agendamento | null;
  options: AgendaOptions;
  weekStart: string;
  busy: boolean;
  error: string | null;
  onClose: () => void;
  onSave: (payload: AgendamentoPayload) => Promise<void>;
  onCancel: (motivo: string) => Promise<void>;
};

export function AgendamentoModal({
  appointment,
  options,
  weekStart,
  busy,
  error,
  onClose,
  onSave,
  onCancel,
}: AgendamentoModalProps) {
  const initial = getInitialForm(appointment, weekStart);
  const [pacienteId, setPacienteId] = useState(initial.pacienteId);
  const [medicoId, setMedicoId] = useState(initial.medicoId);
  const [date, setDate] = useState(initial.date);
  const [startTime, setStartTime] = useState(initial.startTime);
  const [endTime, setEndTime] = useState(initial.endTime);
  const [tipo, setTipo] = useState(initial.tipo);
  const [servicoNome, setServicoNome] = useState(initial.servicoNome);
  const [cancelMode, setCancelMode] = useState(false);
  const [cancelReason, setCancelReason] = useState('');
  const isCanceled = appointment?.status === 'CANCELADO';

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await onSave({
      pacienteId: Number(pacienteId),
      medicoId: medicoId ? Number(medicoId) : null,
      dataHoraInicio: toSaoPauloOffset(date, startTime),
      dataHoraFim: endTime ? toSaoPauloOffset(date, endTime) : null,
      tipo,
      servicoNome,
    });
  }

  async function handleCancel() {
    if (cancelReason.trim()) {
      await onCancel(cancelReason.trim());
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/55 p-4">
      <section
        role="dialog"
        aria-modal="true"
        aria-labelledby="agendamento-modal-title"
        className="max-h-[92vh] w-full max-w-xl overflow-auto rounded-xl border border-clinic-border bg-clinic-surface shadow-2xl custom-scrollbar"
      >
        <div className="flex items-center justify-between border-b border-clinic-border px-5 py-4">
          <div>
            <h2 id="agendamento-modal-title" className="text-sm font-extrabold text-clinic-text">
              {appointment ? 'Editar agendamento' : 'Novo agendamento'}
            </h2>
            <p className="mt-0.5 text-[10px] text-clinic-muted">
              Os dados serão salvos diretamente na agenda da clínica.
            </p>
          </div>
          <button
            type="button"
            aria-label="Fechar"
            onClick={onClose}
            className="rounded-lg p-2 text-clinic-muted transition hover:bg-clinic-hover hover:text-clinic-text"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="grid grid-cols-1 gap-3 p-5 sm:grid-cols-2">
          {error ? (
            <p role="alert" className="sm:col-span-2 rounded-lg border border-clinic-danger/30 bg-clinic-danger/10 px-3 py-2 text-[10px] font-semibold text-clinic-danger">
              {error}
            </p>
          ) : null}

          <Field label="Paciente" className="sm:col-span-2">
            <select
              id="agenda-paciente"
              value={pacienteId}
              onChange={(event) => setPacienteId(event.target.value)}
              required
              disabled={busy || isCanceled}
              className={inputClassName}
            >
              <option value="">Selecione um paciente</option>
              {options.pacientes.filter((paciente) => paciente.id !== null).map((paciente) => (
                <option key={paciente.id} value={paciente.id ?? ''}>{paciente.nome}</option>
              ))}
            </select>
          </Field>

          <Field label="Médico ou profissional" className="sm:col-span-2">
            <select
              id="agenda-medico"
              value={medicoId}
              onChange={(event) => setMedicoId(event.target.value)}
              disabled={busy || isCanceled}
              className={inputClassName}
            >
              <option value="">Sem profissional definido</option>
              {options.medicos.filter((medico) => medico.id !== null).map((medico) => (
                <option key={medico.id} value={medico.id ?? ''}>{medico.nome}</option>
              ))}
            </select>
          </Field>

          <Field label="Data">
            <input
              id="agenda-data"
              type="date"
              value={date}
              onChange={(event) => setDate(event.target.value)}
              required
              disabled={busy || isCanceled}
              className={inputClassName}
            />
          </Field>

          <Field label="Tipo">
            <select
              id="agenda-tipo"
              value={tipo}
              onChange={(event) => setTipo(event.target.value)}
              required
              disabled={busy || isCanceled}
              className={inputClassName}
            >
              <option value="CONSULTA">Consulta</option>
              <option value="RETORNO">Retorno</option>
              <option value="EXAME">Exame</option>
              <option value="CIRURGIA">Cirurgia</option>
              <option value="PROCEDIMENTO">Procedimento</option>
            </select>
          </Field>

          <Field label="Horário inicial">
            <input
              id="agenda-inicio"
              type="time"
              value={startTime}
              onChange={(event) => setStartTime(event.target.value)}
              required
              disabled={busy || isCanceled}
              className={inputClassName}
            />
          </Field>

          <Field label="Horário final">
            <input
              id="agenda-fim"
              type="time"
              value={endTime}
              onChange={(event) => setEndTime(event.target.value)}
              disabled={busy || isCanceled}
              className={inputClassName}
            />
          </Field>

          <Field label="Procedimento" className="sm:col-span-2">
            <input
              id="agenda-procedimento"
              value={servicoNome}
              onChange={(event) => setServicoNome(event.target.value)}
              placeholder="Ex.: Consulta pré-natal"
              required
              maxLength={120}
              disabled={busy || isCanceled}
              className={inputClassName}
            />
          </Field>

          {cancelMode ? (
            <div className="sm:col-span-2 rounded-lg border border-clinic-danger/30 bg-clinic-danger/5 p-3">
              <label htmlFor="agenda-cancelamento" className="text-[10px] font-bold text-clinic-text">
                Motivo do cancelamento
              </label>
              <input
                id="agenda-cancelamento"
                value={cancelReason}
                onChange={(event) => setCancelReason(event.target.value)}
                maxLength={255}
                className={`${inputClassName} mt-1.5`}
              />
              <div className="mt-3 flex justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setCancelMode(false)}
                  className={secondaryButtonClassName}
                >
                  Voltar
                </button>
                <button
                  type="button"
                  disabled={busy || !cancelReason.trim()}
                  onClick={handleCancel}
                  className="rounded-lg bg-clinic-danger px-3 py-2 text-[10px] font-bold text-white disabled:opacity-50"
                >
                  Confirmar cancelamento
                </button>
              </div>
            </div>
          ) : null}

          <div className="sm:col-span-2 mt-1 flex flex-wrap items-center justify-between gap-2 border-t border-clinic-border pt-4">
            <div>
              {appointment && !isCanceled && !cancelMode ? (
                <button
                  type="button"
                  onClick={() => setCancelMode(true)}
                  className="rounded-lg px-3 py-2 text-[10px] font-bold text-clinic-danger transition hover:bg-clinic-danger/10"
                >
                  Cancelar agendamento
                </button>
              ) : null}
              {isCanceled ? (
                <span className="text-[10px] font-bold text-clinic-danger">Agendamento cancelado</span>
              ) : null}
            </div>
            <div className="flex gap-2">
              <button type="button" onClick={onClose} className={secondaryButtonClassName}>
                Fechar
              </button>
              {!isCanceled && !cancelMode ? (
                <button
                  type="submit"
                  disabled={busy}
                  className="rounded-lg bg-clinic-primary px-4 py-2 text-[10px] font-bold text-white disabled:opacity-50"
                >
                  {busy ? 'Salvando...' : 'Salvar agendamento'}
                </button>
              ) : null}
            </div>
          </div>
        </form>
      </section>
    </div>
  );
}

function Field({
  label,
  className = '',
  children,
}: {
  label: string;
  className?: string;
  children: React.ReactNode;
}) {
  const htmlFor = {
    Paciente: 'agenda-paciente',
    'Médico ou profissional': 'agenda-medico',
    Data: 'agenda-data',
    Tipo: 'agenda-tipo',
    'Horário inicial': 'agenda-inicio',
    'Horário final': 'agenda-fim',
    Procedimento: 'agenda-procedimento',
  }[label];

  return (
    <div className={className}>
      <label htmlFor={htmlFor} className="mb-1.5 block text-[10px] font-bold text-clinic-text">
        {label}
      </label>
      {children}
    </div>
  );
}

function getInitialForm(appointment: Agendamento | null, weekStart: string) {
  return {
    pacienteId: appointment?.pacienteId.toString() ?? '',
    medicoId: appointment?.medicoId?.toString() ?? '',
    date: appointment ? formatDate(appointment.dataHoraInicio) : weekStart,
    startTime: appointment ? formatTime(appointment.dataHoraInicio) : '09:00',
    endTime: appointment?.dataHoraFim ? formatTime(appointment.dataHoraFim) : '09:30',
    tipo: appointment?.tipo ?? 'CONSULTA',
    servicoNome: appointment?.servicoNome ?? '',
  };
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Sao_Paulo',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date(value));
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat('pt-BR', {
    timeZone: 'America/Sao_Paulo',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date(value));
}

function toSaoPauloOffset(date: string, time: string) {
  return `${date}T${time}:00-03:00`;
}

const inputClassName = 'h-10 w-full rounded-lg border border-clinic-border bg-clinic-input px-3 text-xs text-clinic-text outline-none transition focus:border-clinic-primary disabled:cursor-not-allowed disabled:opacity-60';
const secondaryButtonClassName = 'rounded-lg border border-clinic-border bg-clinic-surface px-3 py-2 text-[10px] font-bold text-clinic-text transition hover:bg-clinic-hover';
