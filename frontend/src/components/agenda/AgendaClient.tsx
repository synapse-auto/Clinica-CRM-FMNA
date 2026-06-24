'use client';

import { useMemo, useState } from 'react';
import {
  Activity,
  CalendarDays,
  CalendarRange,
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

export function AgendaClient({
  initialAppointments,
  initialOptions,
  initialError,
  weekStart,
}: AgendaClientProps) {
  const [appointments] = useState(initialAppointments);
  const [selectedDoctor, setSelectedDoctor] = useState<number | 'all'>('all');
  const weekDays = useMemo(() => buildWeekDays(weekStart), [weekStart]);
  const activeAppointments = appointments.filter((item) => item.status !== 'CANCELADO');
  const visibleAppointments = selectedDoctor === 'all'
    ? appointments
    : appointments.filter((item) => item.medicoId === selectedDoctor);
  const metrics = buildMetrics(activeAppointments, initialOptions);
  const donutItems = buildDonutItems(activeAppointments);
  const barData = buildBarData(activeAppointments, initialOptions);

  return (
    <div className="h-full overflow-auto bg-clinic-canvas p-4 custom-scrollbar">
      <PageHeader
        title="Agenda"
        description="Agendamentos da semana (somente leitura)"
        actions={
          <button
            type="button"
            className="flex h-8 items-center gap-2 rounded-lg border border-clinic-border bg-clinic-surface px-3 text-[10px] font-bold text-clinic-text"
          >
            <CalendarRange className="h-3.5 w-3.5 text-clinic-primary" />
            Semana atual
          </button>
        }
      />

      {initialError ? (
        <p role="alert" className="mb-3 rounded-lg border border-clinic-danger/30 bg-clinic-danger/10 px-3 py-2 text-[10px] font-semibold text-clinic-danger">
          {initialError}
        </p>
      ) : null}

      <div className="mb-3 grid grid-cols-1 gap-2 sm:grid-cols-2 xl:grid-cols-4">
        <MetricCard icon={Stethoscope} title="Consultas Hoje" value={metrics.today} subtitle="agendamentos ativos" tone="cyan" />
        <MetricCard icon={CalendarDays} title="Total na Semana" value={metrics.week} subtitle="consultas + exames + cirurgias" tone="teal" />
        <MetricCard icon={UserRoundCheck} title="Médicos Disponíveis" value={metrics.doctors} subtitle="profissionais ativos" tone="teal" />
        <MetricCard icon={Activity} title="Confirmações enviadas" value={metrics.confirmations} subtitle="na semana atual" tone="purple" />
      </div>

      <div className="grid grid-cols-1 gap-3 xl:grid-cols-12">
        <DemoCard
          className="xl:col-span-8"
          title="Agendamentos da Semana"
          description="Visão semanal por médico"
          actions={<CalendarDays className="h-4 w-4" />}
        >
          <div className="px-4 pb-4">
            <DoctorFilters
              options={initialOptions}
              selected={selectedDoctor}
              onSelect={setSelectedDoctor}
            />
            <WeekGrid
              days={weekDays}
              appointments={visibleAppointments}
            />
          </div>
        </DemoCard>

        <DemoCard className="xl:col-span-4" title="Tipos de Atendimento" description="Distribuição semanal">
          <div className="flex min-h-[222px] items-center justify-center px-5 pb-4">
            <DonutChart items={donutItems} valueMode="value" />
          </div>
        </DemoCard>
      </div>

      <DemoCard className="mt-3" title="Agenda por Médico" description="Distribuição de atendimentos na semana">
        <GroupedBarChart height={230} labels={barData.labels} series={barData.series} />
      </DemoCard>
    </div>
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
      <div className="grid min-w-[680px] grid-cols-5 gap-2">
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
    week: appointments.length,
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

function buildWeekDays(weekStart: string) {
  const start = new Date(`${weekStart}T12:00:00Z`);
  return Array.from({ length: 5 }, (_, index) => {
    const date = new Date(start);
    date.setUTCDate(start.getUTCDate() + index);
    return {
      date: date.toISOString().slice(0, 10),
      label: new Intl.DateTimeFormat('pt-BR', {
        weekday: 'short',
        day: '2-digit',
        timeZone: 'UTC',
      }).format(date).replace('.', ''),
    };
  });
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


