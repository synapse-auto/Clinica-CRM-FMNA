'use client';

import { useMemo, useState } from 'react';
import {
  Activity,
  CalendarDays,
  CalendarRange,
  Loader2,
  Stethoscope,
  UserRoundCheck,
} from 'lucide-react';
import { DemoCard } from '@/components/demo/DemoCard';
import { DonutChart } from '@/components/demo/DonutChart';
import { GroupedBarChart } from '@/components/demo/GroupedBarChart';
import { MetricCard } from '@/components/demo/MetricCard';
import { PageHeader } from '@/components/demo/PageHeader';
import type {
  Agendamento,
  AgendaOptions,
} from '@/types/agendamento';

type AgendaClientProps = {
  initialAppointments: Agendamento[];
  initialOptions: AgendaOptions;
  initialError: string | null;
  weekStart: string;
};

type AgendaRange = {
  kind: 'week' | 'current-month' | 'previous-month' | 'custom';
  startDate: string;
  endDate: string;
  apiStart: string;
  apiEnd: string;
};

export function AgendaClient({
  initialAppointments,
  initialOptions,
  initialError,
  weekStart,
}: AgendaClientProps) {
  const initialRange = useMemo(() => buildWeekRange(weekStart), [weekStart]);
  const [appointments, setAppointments] = useState(initialAppointments);
  const [selectedDoctor, setSelectedDoctor] = useState<number | 'all'>('all');
  const [range, setRange] = useState<AgendaRange>(initialRange);
  const [error, setError] = useState(initialError);
  const [loading, setLoading] = useState(false);
  const [customStart, setCustomStart] = useState(initialRange.startDate);
  const [customEnd, setCustomEnd] = useState(initialRange.endDate);
  const days = useMemo(() => buildDays(range.startDate, range.endDate), [range]);
  const activeAppointments = appointments.filter((item) => item.status !== 'CANCELADO');
  const visibleAppointments = selectedDoctor === 'all'
    ? appointments
    : appointments.filter((item) => item.medicoId === selectedDoctor);
  const metrics = buildMetrics(activeAppointments, initialOptions);
  const donutItems = buildDonutItems(activeAppointments);
  const barData = buildBarData(activeAppointments, initialOptions);

  async function loadRange(nextRange: AgendaRange) {
    setLoading(true);
    try {
      const search = new URLSearchParams({
        inicio: nextRange.apiStart,
        fim: nextRange.apiEnd,
      });
      const response = await fetch(`/api/agendamentos?${search.toString()}`, {
        headers: { Accept: 'application/json' },
      });
      if (!response.ok) {
        throw new Error('Falha ao carregar agenda');
      }
      const data = await response.json() as Agendamento[];
      setAppointments(data);
      setRange(nextRange);
      setCustomStart(nextRange.startDate);
      setCustomEnd(nextRange.endDate);
      setError(null);
    } catch {
      setError('Não foi possível carregar a agenda para o período selecionado.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="h-full overflow-auto bg-clinic-canvas p-4 custom-scrollbar">
      <PageHeader
        title="Agenda"
        description={`Agendamentos de ${formatDisplayDate(range.startDate)} a ${formatDisplayDate(range.endDate)} (somente leitura)`}
        actions={
          <div className="flex flex-wrap items-center gap-1.5">
            <PeriodButton
              label="Semana atual"
              active={range.kind === 'week'}
              onClick={() => loadRange(buildWeekRange(weekStart))}
            />
            <PeriodButton
              label="Mês atual"
              active={range.kind === 'current-month'}
              onClick={() => loadRange(buildCurrentMonthRange())}
            />
            <PeriodButton
              label="Mês anterior"
              active={range.kind === 'previous-month'}
              onClick={() => loadRange(buildPreviousMonthRange())}
            />
          </div>
        }
      />

      {error ? (
        <p role="alert" className="mb-3 rounded-lg border border-clinic-danger/30 bg-clinic-danger/10 px-3 py-2 text-[10px] font-semibold text-clinic-danger">
          {error}
        </p>
      ) : null}

      <div className="mb-3 flex flex-wrap items-end gap-2 rounded-lg border border-clinic-border bg-clinic-surface px-3 py-2">
        <label className="flex flex-col gap-1 text-[9px] font-bold uppercase text-clinic-muted">
          Início
          <input
            type="date"
            value={customStart}
            onChange={(event) => setCustomStart(event.target.value)}
            className="h-8 rounded-lg border border-clinic-border bg-clinic-canvas px-2 text-[10px] font-semibold normal-case text-clinic-text outline-none focus:border-clinic-primary"
          />
        </label>
        <label className="flex flex-col gap-1 text-[9px] font-bold uppercase text-clinic-muted">
          Fim
          <input
            type="date"
            value={customEnd}
            onChange={(event) => setCustomEnd(event.target.value)}
            className="h-8 rounded-lg border border-clinic-border bg-clinic-canvas px-2 text-[10px] font-semibold normal-case text-clinic-text outline-none focus:border-clinic-primary"
          />
        </label>
        <button
          type="button"
          onClick={() => loadRange(buildCustomRange(customStart, customEnd))}
          className="flex h-8 items-center gap-2 rounded-lg bg-clinic-primary px-3 text-[10px] font-bold text-white transition hover:bg-clinic-primary/90"
        >
          {loading ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <CalendarRange className="h-3.5 w-3.5" />}
          Aplicar período
        </button>
        <span className="text-[10px] font-semibold text-clinic-muted">
          Agendamentos de {formatDisplayDate(range.startDate)} a {formatDisplayDate(range.endDate)}
        </span>
      </div>

      <div className="mb-3 grid grid-cols-1 gap-2 sm:grid-cols-2 xl:grid-cols-4">
        <MetricCard icon={Stethoscope} title="Consultas Hoje" value={metrics.today} subtitle="agendamentos ativos" tone="cyan" />
        <MetricCard icon={CalendarDays} title="Total no período" value={metrics.period} subtitle="consultas + exames + cirurgias" tone="teal" />
        <MetricCard icon={UserRoundCheck} title="Médicos Disponíveis" value={metrics.doctors} subtitle="profissionais ativos" tone="teal" />
        <MetricCard icon={Activity} title="Confirmações enviadas" value={metrics.confirmations} subtitle="no período selecionado" tone="purple" />
      </div>

      <div className="grid grid-cols-1 gap-3 xl:grid-cols-12">
        <DemoCard
          className="xl:col-span-8"
          title="Agendamentos do Período"
          description="Visão por dia e profissional"
          actions={<CalendarDays className="h-4 w-4" />}
        >
          <div className="px-4 pb-4">
            <DoctorFilters
              options={initialOptions}
              selected={selectedDoctor}
              onSelect={setSelectedDoctor}
            />
            <WeekGrid
              days={days}
              appointments={visibleAppointments}
            />
          </div>
        </DemoCard>

        <DemoCard className="xl:col-span-4" title="Tipos de Atendimento" description="Distribuição no período">
          <div className="flex min-h-[222px] items-center justify-center px-5 pb-4">
            <DonutChart items={donutItems} valueMode="value" />
          </div>
        </DemoCard>
      </div>

      <DemoCard className="mt-3" title="Agenda por Médico" description="Distribuição de atendimentos no período">
        <GroupedBarChart height={230} labels={barData.labels} series={barData.series} />
      </DemoCard>
    </div>
  );
}

function PeriodButton({
  label,
  active,
  onClick,
}: {
  label: string;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={active
        ? 'flex h-8 items-center gap-2 rounded-lg bg-clinic-primary px-3 text-[10px] font-bold text-white'
        : 'flex h-8 items-center gap-2 rounded-lg border border-clinic-border bg-clinic-surface px-3 text-[10px] font-bold text-clinic-text transition hover:bg-clinic-hover'}
    >
      <CalendarRange className="h-3.5 w-3.5" />
      {label}
    </button>
  );
}

function DoctorFilters({
  options,
  selected,
  onSelect,
}: {
  options: AgendaOptions;
  selected: number | 'all';
  onSelect: (id: number | 'all') => void;
}) {
  const filters = [{ id: 'all' as const, nome: 'Todos' }, ...options.medicos];
  return (
    <div className="mb-3 flex flex-wrap gap-1.5">
      {filters.map((doctor) => (
        <button
          key={doctor.id}
          type="button"
          onClick={() => onSelect(doctor.id)}
          className={selected === doctor.id
            ? 'rounded-lg bg-clinic-primary px-3 py-1.5 text-[9px] font-bold text-white'
            : 'rounded-lg bg-clinic-surface-muted px-3 py-1.5 text-[9px] font-semibold text-clinic-muted transition hover:bg-clinic-hover hover:text-clinic-text'}
        >
          {doctor.nome}
        </button>
      ))}
    </div>
  );
}

function WeekGrid({
  days,
  appointments,
}: {
  days: Array<{ date: string; label: string }>;
  appointments: Agendamento[];
}) {
  return (
    <div className="overflow-x-auto custom-scrollbar">
      <div
        className="grid min-w-[680px] gap-2"
        style={{ gridTemplateColumns: `repeat(${days.length}, minmax(136px, 1fr))` }}
      >
        {days.map((day) => {
          const dayAppointments = appointments.filter(
            (appointment) => formatDate(appointment.dataHoraInicio) === day.date,
          );
          return (
            <div key={day.date} className="min-w-0">
              <p className="mb-2 border-b border-clinic-border pb-1.5 text-center text-[9px] font-bold text-clinic-text">
                {day.label}
              </p>
              <div className="space-y-1.5">
                {dayAppointments.map((appointment) => (
                  <AppointmentCard key={appointment.id} appointment={appointment} />
                ))}
                {dayAppointments.length === 0 ? (
                  <p className="rounded-lg border border-dashed border-clinic-border px-2 py-3 text-center text-[8px] text-clinic-muted">
                    Sem agendamentos
                  </p>
                ) : null}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function AppointmentCard({
  appointment,
}: {
  appointment: Agendamento;
}) {
  const canceled = appointment.status === 'CANCELADO';
  return (
    <div
      aria-label={`${appointment.pacienteNome}, ${appointment.servicoNome}, ${formatTime(appointment.dataHoraInicio)}`}
      className={`min-h-12 w-full rounded-lg px-2.5 py-2 ${
        canceled ? 'bg-clinic-danger/10 opacity-70' : 'bg-clinic-cyan/15'
      }`}
    >
      <p className={`truncate text-[9px] font-extrabold ${canceled ? 'line-through text-clinic-muted' : 'text-clinic-text'}`}>
        {appointment.pacienteNome}
      </p>
      <p className="mt-0.5 truncate text-[8px] text-clinic-muted">
        {formatTime(appointment.dataHoraInicio)} · {appointment.medicoNome ?? 'Sem profissional'}
      </p>
      {canceled ? <span className="text-[7px] font-bold uppercase text-clinic-danger">Cancelado</span> : null}
    </div>
  );
}

function buildMetrics(
  appointments: Agendamento[],
  options: AgendaOptions,
) {
  const today = formatDate(new Date().toISOString());
  return {
    today: appointments.filter((item) => formatDate(item.dataHoraInicio) === today).length,
    period: appointments.length,
    doctors: options.medicos.length,
    confirmations: appointments.filter((item) => (item.confirmacaoEnviada ?? 0) > 0).length,
  };
}

function buildDonutItems(appointments: Agendamento[]) {
  const definitions = [
    { key: 'CONSULTA', label: 'Consultas', color: 'var(--clinic-primary)' },
    { key: 'EXAME', label: 'Exames', color: 'var(--clinic-cyan)' },
    { key: 'CIRURGIA', label: 'Cirurgias', color: 'var(--clinic-indigo)' },
    { key: 'RETORNO', label: 'Retornos', color: 'var(--clinic-orange)' },
  ];
  return definitions.map((definition) => ({
    label: definition.label,
    color: definition.color,
    value: appointments.filter((item) => item.tipo === definition.key).length,
  }));
}

function buildBarData(appointments: Agendamento[], options: AgendaOptions) {
  const doctors = options.medicos.length > 0
    ? options.medicos
    : [{ id: -1, nome: 'Sem profissional' }];
  const definitions = [
    { key: 'CONSULTA', label: 'Consultas', color: 'var(--clinic-primary)' },
    { key: 'EXAME', label: 'Exames', color: 'var(--clinic-orange)' },
    { key: 'CIRURGIA', label: 'Cirurgias', color: 'var(--clinic-indigo)' },
  ];
  return {
    labels: doctors.map((doctor) => doctor.nome),
    series: definitions.map((definition) => ({
      label: definition.label,
      color: definition.color,
      values: doctors.map((doctor) => appointments.filter((item) => (
        item.tipo === definition.key
        && (doctor.id === -1 ? item.medicoId === null : item.medicoId === doctor.id)
      )).length),
    })),
  };
}

function buildWeekRange(weekStart: string): AgendaRange {
  return buildRange('week', weekStart, addDays(weekStart, 4));
}

function buildCurrentMonthRange(): AgendaRange {
  const today = currentSaoPauloDate();
  const start = new Date(today.getFullYear(), today.getMonth(), 1);
  const end = new Date(today.getFullYear(), today.getMonth() + 1, 0);
  return buildRange('current-month', formatLocalDate(start), formatLocalDate(end));
}

function buildPreviousMonthRange(): AgendaRange {
  const today = currentSaoPauloDate();
  const start = new Date(today.getFullYear(), today.getMonth() - 1, 1);
  const end = new Date(today.getFullYear(), today.getMonth(), 0);
  return buildRange('previous-month', formatLocalDate(start), formatLocalDate(end));
}

function buildCustomRange(startDate: string, endDate: string): AgendaRange {
  const safeStart = startDate || endDate || formatLocalDate(currentSaoPauloDate());
  const safeEnd = endDate && endDate >= safeStart ? endDate : safeStart;
  return buildRange('custom', safeStart, safeEnd);
}

function buildRange(kind: AgendaRange['kind'], startDate: string, endDate: string): AgendaRange {
  return {
    kind,
    startDate,
    endDate,
    apiStart: `${startDate}T00:00:00-03:00`,
    apiEnd: `${addDays(endDate, 1)}T00:00:00-03:00`,
  };
}

function buildDays(startDate: string, endDate: string) {
  const start = parseLocalDate(startDate);
  const end = parseLocalDate(endDate);
  const length = Math.max(1, Math.round((end.getTime() - start.getTime()) / 86_400_000) + 1);
  return Array.from({ length }, (_, index) => {
    const date = new Date(start);
    date.setUTCDate(start.getUTCDate() + index);
    const isoDate = date.toISOString().slice(0, 10);
    return {
      date: isoDate,
      label: new Intl.DateTimeFormat('pt-BR', {
        weekday: 'short',
        day: '2-digit',
        timeZone: 'UTC',
      }).format(date).replace('.', ''),
    };
  });
}

function currentSaoPauloDate() {
  return new Date(new Date().toLocaleString('en-US', {
    timeZone: 'America/Sao_Paulo',
  }));
}

function addDays(dateValue: string, amount: number) {
  const date = parseLocalDate(dateValue);
  date.setUTCDate(date.getUTCDate() + amount);
  return date.toISOString().slice(0, 10);
}

function parseLocalDate(dateValue: string) {
  return new Date(`${dateValue}T12:00:00Z`);
}

function formatLocalDate(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function formatDisplayDate(dateValue: string) {
  const [year, month, day] = dateValue.split('-');
  return `${day}/${month}/${year}`;
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
