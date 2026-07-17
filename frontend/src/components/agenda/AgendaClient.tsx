'use client';

import { useEffect, useMemo, useState } from 'react';
import {
  Activity,
  CalendarDays,
  CalendarRange,
  ChevronDown,
  ChevronUp,
  Loader2,
  Search,
  Stethoscope,
  UserRoundCheck,
  X,
} from 'lucide-react';
import { DemoCard } from '@/components/demo/DemoCard';
import { DonutChart } from '@/components/demo/DonutChart';
import { GroupedBarChart } from '@/components/demo/GroupedBarChart';
import { MetricCard } from '@/components/demo/MetricCard';
import { PageHeader } from '@/components/demo/PageHeader';
import { DatePicker } from '@/components/ui/date-picker';
import { normalizeSearchText } from '@/lib/search';
import {
  aggregateServices,
  normalizeServiceName,
  serviceColor,
} from '@/lib/service-distribution';
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

export type AgendaAppointmentGroup = {
  key: string;
  appointments: Agendamento[];
  appointmentIds: number[];
};

export function AgendaClient({
  initialAppointments,
  initialOptions,
  initialError,
  weekStart,
}: AgendaClientProps) {
  const initialRange = useMemo(() => buildWeekRange(weekStart), [weekStart]);
  const [appointments, setAppointments] = useState(initialAppointments);
  const [options, setOptions] = useState(initialOptions);
  const [selectedDoctor, setSelectedDoctor] = useState<string | 'all'>('all');
  const [patientSearch, setPatientSearch] = useState('');
  const [range, setRange] = useState<AgendaRange>(initialRange);
  const [error, setError] = useState(initialError);
  const [loading, setLoading] = useState(false);
  const [customStart, setCustomStart] = useState(initialRange.startDate);
  const [customEnd, setCustomEnd] = useState(initialRange.endDate);
  const [selectedDay, setSelectedDay] = useState(initialRange.startDate);
  const [expandedGroups, setExpandedGroups] = useState<Set<string>>(new Set());
  const days = useMemo(
    () => buildDays(range.startDate, range.endDate),
    [range.startDate, range.endDate],
  );
  const activeAppointments = useMemo(
    () => appointments.filter((item) => item.status !== 'CANCELADO'),
    [appointments],
  );
  const normalizedPatientSearch = normalizeSearchText(patientSearch);
  const visibleAppointments = useMemo(() => appointments.filter((item) => (
    (selectedDoctor === 'all' || appointmentDoctorKey(item) === selectedDoctor)
    && (!normalizedPatientSearch || normalizeSearchText(item.pacienteNome).includes(normalizedPatientSearch))
  )), [appointments, normalizedPatientSearch, selectedDoctor]);
  const selectedDayAppointments = useMemo(
    () => visibleAppointments.filter(
      (appointment) => formatDate(appointment.dataHoraInicio) === selectedDay,
    ),
    [selectedDay, visibleAppointments],
  );
  const filteredActiveAppointments = useMemo(
    () => visibleAppointments.filter((item) => item.status !== 'CANCELADO'),
    [visibleAppointments],
  );
  const metrics = buildMetrics(activeAppointments, options);
  const donutItems = buildDonutItems(filteredActiveAppointments);
  const barData = buildBarData(filteredActiveAppointments, options, selectedDoctor);

  useEffect(() => {
    setSelectedDay(preferredDay(days, visibleAppointments));
    setExpandedGroups(new Set());
  }, [days, visibleAppointments]);

  function selectDay(day: string) {
    setSelectedDay(day);
    setExpandedGroups(new Set());
  }

  function toggleGroup(groupKey: string) {
    setExpandedGroups((current) => {
      const next = new Set(current);
      if (next.has(groupKey)) next.delete(groupKey);
      else next.add(groupKey);
      return next;
    });
  }

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
      const [data, nextOptions] = await Promise.all([
        response.json() as Promise<Agendamento[]>,
        fetch(`/api/agendamentos/opcoes?${search.toString()}`, {
          headers: { Accept: 'application/json' },
        }).then(async (optionsResponse) => {
          if (!optionsResponse.ok) throw new Error('Falha ao carregar profissionais da agenda');
          return optionsResponse.json() as Promise<AgendaOptions>;
        }),
      ]);
      setAppointments(data);
      setOptions(nextOptions);
      setSelectedDoctor('all');
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
          <DatePicker
            aria-label="Início"
            value={customStart}
            onValueChange={setCustomStart}
            className="h-8 rounded-lg border border-clinic-border bg-clinic-canvas px-2 text-[10px] font-semibold normal-case text-clinic-text outline-none focus:border-clinic-primary"
          />
        </label>
        <label className="flex flex-col gap-1 text-[9px] font-bold uppercase text-clinic-muted">
          Fim
          <DatePicker
            aria-label="Fim"
            value={customEnd}
            onValueChange={setCustomEnd}
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
        <MetricCard icon={UserRoundCheck} title="Profissionais no período" value={metrics.doctors} subtitle="encontrados na agenda" tone="teal" />
        <MetricCard icon={Activity} title="Confirmações enviadas" value={metrics.confirmations} subtitle="no período selecionado" tone="purple" />
      </div>

      <DemoCard
        title="Agendamentos do Período"
        description="Selecione um dia para consultar os horários"
        actions={<CalendarDays className="h-4 w-4" />}
      >
        <div className="px-4 pb-4">
          <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
            <label className="flex h-9 min-w-0 flex-1 items-center gap-2 rounded-lg border border-clinic-border bg-clinic-input px-2 text-clinic-muted focus-within:ring-2 focus-within:ring-clinic-primary/35 sm:max-w-sm">
              <Search className="h-3.5 w-3.5 shrink-0" />
              <input
                type="search"
                value={patientSearch}
                onChange={(event) => setPatientSearch(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === 'Escape') setPatientSearch('');
                }}
                aria-label="Buscar paciente na agenda"
                placeholder="Buscar paciente na agenda..."
                className="min-w-0 flex-1 bg-transparent text-[11px] font-semibold text-clinic-text outline-none placeholder:text-clinic-muted"
              />
              {patientSearch ? (
                <button type="button" aria-label="Limpar pesquisa de paciente" onClick={() => setPatientSearch('')} className="rounded p-0.5 hover:bg-clinic-hover hover:text-clinic-text">
                  <X className="h-3.5 w-3.5" />
                </button>
              ) : null}
            </label>
            <p className="text-[10px] font-semibold text-clinic-muted">{visibleAppointments.length} agendamentos encontrados</p>
          </div>
          <DoctorFilters
            options={options}
            selected={selectedDoctor}
            onSelect={setSelectedDoctor}
          />
          <DayTabs
            days={days}
            appointments={visibleAppointments}
            selectedDay={selectedDay}
            onSelect={selectDay}
          />
          <CompactAgendaDay
            date={selectedDay}
            appointments={selectedDayAppointments}
            expandedGroups={expandedGroups}
            onToggleGroup={toggleGroup}
            emptyMessage={normalizedPatientSearch ? 'Nenhum agendamento encontrado para este paciente no per\u00edodo selecionado.' : 'Sem agendamentos'}
          />
        </div>
      </DemoCard>

      <DemoCard className="mt-3" title="Tipos de Atendimento" description="Distribuição no período">
        {donutItems.length > 0 ? (
          <div className="flex min-h-[190px] items-center justify-center px-5 pb-4">
            <DonutChart items={donutItems} valueMode="value" />
          </div>
        ) : (
          <p className="flex min-h-[190px] items-center justify-center px-5 pb-4 text-center text-[10px] text-clinic-muted">
            Sem dados de serviços no período.
          </p>
        )}
      </DemoCard>

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
  selected: string | 'all';
  onSelect: (id: string | 'all') => void;
}) {
  const filters: Array<DoctorFilter> = [
    { id: 'all', nome: 'Todos' },
    ...options.medicos,
  ];
  return (
    <div className="mb-3 flex gap-1.5 overflow-x-auto pb-1 custom-scrollbar">
      {filters.map((doctor) => (
        <button
          key={filterKey(doctor)}
          type="button"
          onClick={() => onSelect(filterKey(doctor))}
          className={selected === filterKey(doctor)
            ? 'shrink-0 rounded-lg bg-clinic-primary px-3 py-1.5 text-[9px] font-bold text-white'
            : 'shrink-0 rounded-lg bg-clinic-surface-muted px-3 py-1.5 text-[9px] font-semibold text-clinic-muted transition hover:bg-clinic-hover hover:text-clinic-text'}
        >
          {doctor.nome}
        </button>
      ))}
    </div>
  );
}

type DoctorFilter = { id: 'all'; nome: string } | AgendaOptions['medicos'][number];

function filterKey(doctor: DoctorFilter): string {
  return doctor.id === 'all' ? 'all' : doctorKey(doctor);
}

function DayTabs({
  days,
  appointments,
  selectedDay,
  onSelect,
}: {
  days: Array<{ date: string; label: string }>;
  appointments: Agendamento[];
  selectedDay: string;
  onSelect: (date: string) => void;
}) {
  return (
    <div className="mb-3 flex gap-1.5 overflow-x-auto border-b border-clinic-border pb-2 custom-scrollbar">
      {days.map((day) => {
        const count = countAppointmentsOnDay(appointments, day.date);
        const selected = selectedDay === day.date;
        return (
          <button
            key={day.date}
            type="button"
            aria-label={`${day.label}: ${count} ${count === 1 ? 'agendamento' : 'agendamentos'}`}
            aria-pressed={selected}
            onClick={() => onSelect(day.date)}
            className={selected
              ? 'flex min-w-[72px] shrink-0 flex-col items-center rounded-lg bg-clinic-primary px-3 py-2 text-white'
              : 'flex min-w-[72px] shrink-0 flex-col items-center rounded-lg bg-clinic-surface-muted px-3 py-2 text-clinic-muted transition hover:bg-clinic-hover hover:text-clinic-text'}
          >
            <span className="text-[9px] font-bold capitalize">{day.label}</span>
            <span className="mt-0.5 text-[11px] font-extrabold">{count}</span>
          </button>
        );
      })}
    </div>
  );
}

function CompactAgendaDay({
  date,
  appointments,
  expandedGroups,
  onToggleGroup,
  emptyMessage,
}: {
  date: string;
  appointments: Agendamento[];
  expandedGroups: Set<string>;
  onToggleGroup: (groupKey: string) => void;
  emptyMessage: string;
}) {
  const groups = groupAppointmentsForAgenda(appointments);
  return (
    <section aria-label={`Agenda de ${formatDisplayDate(date)}`}>
      <div className="mb-2 flex items-center justify-between gap-3">
        <p className="text-[10px] font-bold text-clinic-text">
          {formatDisplayDate(date)}
        </p>
        <p className="text-[9px] font-semibold text-clinic-muted">
          {appointments.length} {appointments.length === 1 ? 'agendamento' : 'agendamentos'}
        </p>
      </div>
      <div className="max-h-[520px] space-y-1.5 overflow-y-auto pr-1 custom-scrollbar">
        {groups.map((group) => (
          <CompactAppointmentRow
            key={group.key}
            group={group}
            expanded={expandedGroups.has(group.key)}
            onToggle={() => onToggleGroup(group.key)}
          />
        ))}
        {groups.length === 0 ? (
          <p className="rounded-lg border border-dashed border-clinic-border px-3 py-6 text-center text-[9px] text-clinic-muted">
            {emptyMessage}
          </p>
        ) : null}
      </div>
    </section>
  );
}

function CompactAppointmentRow({
  group,
  expanded,
  onToggle,
}: {
  group: AgendaAppointmentGroup;
  expanded: boolean;
  onToggle: () => void;
}) {
  const appointment = group.appointments[0];
  const canceled = appointment.status === 'CANCELADO';
  const procedures = group.appointments.map(appointmentProcedureName);
  const statusLabel = formatStatus(appointment.status);
  const expandable = procedures.length > 1;
  const detailsId = `agenda-group-${group.appointmentIds.join('-')}`;
  return (
    <div className={`overflow-hidden rounded-lg border ${
      canceled
        ? 'border-clinic-danger/25 bg-clinic-danger/10'
        : 'border-clinic-border bg-clinic-surface-muted/70'
    }`}>
      <button
        type="button"
        aria-label={`${appointment.pacienteNome}, ${procedures.length === 1 ? procedures[0] : `${procedures.length} procedimentos`}, ${formatTime(appointment.dataHoraInicio)}, ${statusLabel}`}
        aria-expanded={expandable ? expanded : undefined}
        aria-controls={expandable ? detailsId : undefined}
        data-appointment-ids={group.appointmentIds.join(',')}
        onClick={expandable ? onToggle : undefined}
        className={`grid w-full grid-cols-[52px_minmax(0,1.2fr)] gap-x-3 gap-y-1 px-3 py-2 text-left sm:grid-cols-[56px_minmax(0,1.4fr)_minmax(0,1fr)_minmax(0,1.2fr)_auto] sm:items-center ${
          expandable ? 'cursor-pointer transition hover:bg-clinic-hover' : 'cursor-default'
        }`}
      >
        <span className="row-span-2 text-[11px] font-extrabold text-clinic-primary sm:row-span-1">
          {formatTime(appointment.dataHoraInicio)}
        </span>
        <span className={`min-w-0 truncate text-[10px] font-extrabold ${
          canceled ? 'line-through text-clinic-muted' : 'text-clinic-text'
        }`}>
          {appointment.pacienteNome}
        </span>
        <span className="min-w-0 truncate text-[9px] font-semibold text-clinic-muted">
          {appointment.medicoNome ?? 'Sem profissional'}
        </span>
        <span className="flex min-w-0 items-center gap-1.5 text-[9px] font-semibold text-clinic-text">
          <span className="truncate">
            {procedures.length === 1 ? procedures[0] : `${procedures.length} procedimentos`}
          </span>
          {expandable ? (
            expanded
              ? <ChevronUp className="h-3.5 w-3.5 shrink-0 text-clinic-muted" />
              : <ChevronDown className="h-3.5 w-3.5 shrink-0 text-clinic-muted" />
          ) : null}
        </span>
        <span className={`w-fit rounded-full px-2 py-0.5 text-[7px] font-bold uppercase ${
          canceled
            ? 'bg-clinic-danger/15 text-clinic-danger'
            : 'bg-clinic-primary/10 text-clinic-primary'
        }`}>
          {statusLabel}
        </span>
      </button>
      {expandable && expanded ? (
        <ul
          id={detailsId}
          className="space-y-1 border-t border-clinic-border px-3 py-2 text-[9px] leading-snug text-clinic-muted"
        >
          {procedures.map((procedure, index) => (
            <li key={`${group.appointmentIds[index]}:${procedure}`} className="flex gap-1.5">
              <span aria-hidden="true">&bull;</span>
              <span>{procedure}</span>
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  );
}

function preferredDay(
  days: Array<{ date: string }>,
  appointments: Agendamento[],
) {
  const today = formatDate(new Date().toISOString());
  const todayInRange = days.some((day) => day.date === today);
  if (todayInRange && countAppointmentsOnDay(appointments, today) > 0) {
    return today;
  }
  const firstDayWithAppointments = days.find(
    (day) => countAppointmentsOnDay(appointments, day.date) > 0,
  );
  return firstDayWithAppointments?.date ?? days[0]?.date ?? today;
}

function countAppointmentsOnDay(appointments: Agendamento[], date: string) {
  return appointments.filter(
    (appointment) => formatDate(appointment.dataHoraInicio) === date,
  ).length;
}

export function groupAppointmentsForAgenda(
  appointments: Agendamento[],
): AgendaAppointmentGroup[] {
  const groups = new Map<string, Agendamento[]>();

  appointments.forEach((appointment) => {
    const identity = [
      `patient:${appointment.pacienteId}`,
      `start:${new Date(appointment.dataHoraInicio).toISOString()}`,
      `doctor:${appointmentDoctorIdentity(appointment)}`,
      `status:${appointment.status}`,
    ].join('|');
    const current = groups.get(identity) ?? [];
    current.push(appointment);
    groups.set(identity, current);
  });

  return Array.from(groups.entries())
    .map(([identity, groupAppointments]) => {
      const sortedAppointments = [...groupAppointments].sort((left, right) => (
        appointmentProcedureName(left).localeCompare(
          appointmentProcedureName(right),
          'pt-BR',
          { sensitivity: 'base' },
        ) || left.id - right.id
      ));
      const appointmentIds = sortedAppointments.map((appointment) => appointment.id);
      return {
        key: `${identity}|ids:${[...appointmentIds].sort((left, right) => left - right).join(',')}`,
        appointments: sortedAppointments,
        appointmentIds,
      };
    })
    .sort((left, right) => {
      const leftAppointment = left.appointments[0];
      const rightAppointment = right.appointments[0];
      return new Date(leftAppointment.dataHoraInicio).getTime()
        - new Date(rightAppointment.dataHoraInicio).getTime()
        || leftAppointment.pacienteNome.localeCompare(
          rightAppointment.pacienteNome,
          'pt-BR',
          { sensitivity: 'base' },
        )
        || appointmentProcedureName(leftAppointment).localeCompare(
          appointmentProcedureName(rightAppointment),
          'pt-BR',
          { sensitivity: 'base' },
        );
    });
}

function appointmentProcedureName(appointment: Agendamento) {
  const serviceName = appointment.servicoNome?.trim();
  if (serviceName) return serviceName;
  return normalizeServiceName(appointment);
}

function formatStatus(status: string) {
  return status
    .toLocaleLowerCase('pt-BR')
    .replaceAll('_', ' ')
    .replace(/^./, (letter) => letter.toLocaleUpperCase('pt-BR'));
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
  return aggregateServices(appointments).map((item, index) => ({
    label: item.label,
    color: serviceColor(index),
    value: item.total,
  }));
}

function buildBarData(
  appointments: Agendamento[],
  options: AgendaOptions,
  selectedDoctor: string | 'all',
) {
  const doctors = buildDoctorBuckets(appointments, options, selectedDoctor);
  const serviceCatalog = aggregateServices(appointments);
  const visibleServices = serviceCatalog.slice(0, 6);
  const visibleLabels = new Set(visibleServices.map((item) => item.label));
  const hasOverflow = serviceCatalog.length > visibleServices.length;

  return {
    labels: doctors.map((doctor) => doctor.label),
    series: [
      ...visibleServices.map((service, index) => ({
        label: service.label,
        color: serviceColor(index),
        values: doctors.map((doctor) => countServiceForDoctor(appointments, doctor.key, service.label)),
      })),
      ...(hasOverflow ? [{
        label: 'Outros',
        color: serviceColor(visibleServices.length),
        values: doctors.map((doctor) => appointments.filter((item) => (
          appointmentDoctorKey(item) === doctor.key
          && !visibleLabels.has(normalizeServiceName(item))
        )).length),
      }] : []),
    ],
  };
}

function buildDoctorBuckets(
  appointments: Agendamento[],
  options: AgendaOptions,
  selectedDoctor: string | 'all',
) {
  const buckets = new Map<string, { key: string; label: string }>();
  options.medicos.forEach((doctor) => {
    const key = doctorKey(doctor);
    buckets.set(key, { key, label: doctor.nome || 'Sem profissional' });
  });
  appointments.forEach((appointment) => {
    const key = appointmentDoctorKey(appointment);
    if (!buckets.has(key)) {
      buckets.set(key, { key, label: appointment.medicoNome?.trim() || 'Sem profissional' });
    }
  });

  const all = Array.from(buckets.values());
  return selectedDoctor === 'all' ? all : all.filter((doctor) => doctor.key === selectedDoctor);
}

function countServiceForDoctor(appointments: Agendamento[], doctorKeyValue: string, serviceLabel: string) {
  return appointments.filter((appointment) => (
    appointmentDoctorKey(appointment) === doctorKeyValue
    && normalizeServiceName(appointment) === serviceLabel
  )).length;
}

function doctorKey(doctor: { id: number | null; nome: string; codigoExterno: string | null; origem: string | null }) {
  if (doctor.id != null) return `local:${doctor.id}`;
  if (doctor.codigoExterno?.trim()) return `external:${doctor.codigoExterno.trim()}`;
  return `name:${normalizeDoctorName(doctor.nome)}`;
}

function appointmentDoctorKey(appointment: Agendamento) {
  return appointmentDoctorIdentity(appointment);
}

function appointmentDoctorIdentity(appointment: Agendamento) {
  if (appointment.medicoId != null) return `local:${appointment.medicoId}`;
  if (appointment.medicoExternalId?.trim()) {
    return `external:${appointment.medicoExternalId.trim()}`;
  }
  return `name:${normalizeDoctorName(appointment.medicoNome ?? 'sem profissional')}`;
}

function normalizeDoctorName(value: string) {
  return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').trim().replace(/\s+/g, ' ').toLocaleLowerCase('pt-BR');
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
