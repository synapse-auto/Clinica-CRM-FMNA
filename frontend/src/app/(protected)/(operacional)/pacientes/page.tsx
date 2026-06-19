import {
  Activity,
  Filter,
  Grid2X2,
  Mail,
  Phone,
  Search,
  UserPlus,
  Users,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { DemoTable } from '@/components/demo/DemoTable';
import { StatusBadge } from '@/components/demo/StatusBadge';
import {
  demoPacientes,
  demoPacientesResumo,
  type DemoPaciente,
} from '@/mocks/demoOperacional';

const avatarTones = {
  green: 'bg-clinic-success text-white',
  blue: 'bg-clinic-blue text-white',
  pink: 'bg-clinic-pink text-white',
  orange: 'bg-clinic-orange text-white',
  purple: 'bg-clinic-indigo text-white',
};

const tagTones: Record<string, string> = {
  Cirurgia: 'bg-clinic-primary/15 text-clinic-primary',
  Particular: 'bg-clinic-success/15 text-clinic-success',
  'Consulta Pré-natal': 'bg-clinic-blue/15 text-clinic-blue',
  'Plano de Saúde': 'bg-clinic-orange/15 text-clinic-orange',
  'Alta Prioridade': 'bg-clinic-danger/15 text-clinic-danger',
  'Novo Paciente': 'bg-clinic-indigo/15 text-clinic-indigo',
  'Follow-up': 'bg-clinic-pink/15 text-clinic-pink',
  Retorno: 'bg-clinic-orange/15 text-clinic-orange',
  VIP: 'bg-clinic-orange/15 text-clinic-orange',
};

export default function PacientesPage() {
  const status = demoPacientesResumo.status;

  return (
    <div className="h-full overflow-auto bg-clinic-canvas p-4 custom-scrollbar">
      <header className="mb-3 border-b border-clinic-border pb-3">
        <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
          <div>
            <h1 className="text-[17px] font-extrabold leading-6 text-clinic-text">Pacientes</h1>
            <p className="text-[10px] text-clinic-muted">{demoPacientesResumo.total} pacientes</p>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <HeaderButton icon={Grid2X2} label="Ver em Kanban" />
            <HeaderButton icon={Filter} label="Filtros" />
            <button
              type="button"
              className="flex h-8 items-center gap-2 rounded-lg bg-clinic-primary px-3 text-[10px] font-bold text-white shadow-sm transition hover:bg-clinic-primary-strong"
            >
              <UserPlus className="h-3.5 w-3.5" />
              Novo Paciente
            </button>
          </div>
        </div>

        <label className="relative mt-3 block max-w-sm">
          <Search className="absolute left-3 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-clinic-muted" />
          <input
            type="search"
            placeholder="Buscar por nome, telefone ou e-mail..."
            className="h-9 w-full rounded-lg border border-clinic-border bg-clinic-input pl-9 pr-3 text-[10px] text-clinic-text outline-none transition placeholder:text-clinic-muted focus:border-clinic-primary focus:ring-2 focus:ring-clinic-primary/10"
          />
        </label>

        <div className="mt-2 flex flex-wrap gap-1.5">
          <FilterChip label="Em Atendimento" value={status.emAtendimento} />
          <FilterChip label="Agendado" value={status.agendado} />
          <FilterChip label="Follow UP" value={status.followUp} />
          <FilterChip label="Finalizado" value={status.finalizado} />
        </div>
      </header>

      <div className="mb-3 grid grid-cols-1 gap-3 xl:grid-cols-[minmax(0,1fr)_180px]">
        <section className="flex min-h-[64px] flex-wrap items-center gap-3 rounded-xl border border-clinic-border bg-clinic-surface px-4 py-3 shadow-[0_1px_2px_rgba(4,32,36,0.04)]">
          <div className="flex items-center gap-2 border-r border-clinic-border pr-3 text-[10px] font-bold text-clinic-text">
            <Activity className="h-4 w-4 text-clinic-primary" />
            Status Atual
          </div>
          <StatusCount tone="green" value={status.emAtendimento} label="Em Atendimento" />
          <StatusCount tone="blue" value={status.agendado} label="Agendados" />
          <StatusCount tone="orange" value={status.followUp} label="Follow UP" />
          <StatusCount tone="slate" value={status.finalizado} label="Finalizados" />
        </section>

        <SummaryCard
          icon={Users}
          value={demoPacientesResumo.total.toString()}
          label="Total de Pacientes"
        />
      </div>

      <DemoTable
        data={demoPacientes}
        getKey={(item) => item.id}
        columns={[
          {
            key: 'contato',
            label: 'Contato',
            className: 'min-w-[260px] w-[27%]',
            render: (item) => <PatientContact patient={item} />,
          },
          {
            key: 'status',
            label: 'Status',
            className: 'min-w-[130px] w-[14%]',
            render: (item) => (
              <StatusBadge tone={patientStatusTone(item.status)}>{item.status}</StatusBadge>
            ),
          },
          {
            key: 'telefone',
            label: 'Telefone',
            className: 'min-w-[145px] w-[15%]',
            render: (item) => (
              <span className="flex items-center gap-1.5 whitespace-nowrap font-semibold text-clinic-text">
                <Phone className="h-3 w-3 text-clinic-muted" />
                {item.telefone}
              </span>
            ),
          },
          {
            key: 'tags',
            label: 'Tags',
            className: 'min-w-[245px] w-[25%]',
            render: (item) => <PatientTags tags={item.tags} />,
          },
          {
            key: 'atendente',
            label: 'Atendente',
            className: 'min-w-[115px] w-[9%]',
            render: (item) => <Attendant patient={item} />,
          },
        ]}
      />
    </div>
  );
}

function HeaderButton({
  icon: Icon,
  label,
}: {
  icon: LucideIcon;
  label: string;
}) {
  return (
    <button
      type="button"
      className="flex h-8 items-center gap-2 rounded-lg border border-clinic-border bg-clinic-surface px-3 text-[10px] font-bold text-clinic-text transition hover:bg-clinic-hover"
    >
      <Icon className="h-3.5 w-3.5 text-clinic-muted" />
      {label}
    </button>
  );
}

function FilterChip({ label, value }: { label: string; value: number }) {
  return (
    <button
      type="button"
      className="rounded-full border border-clinic-border bg-clinic-surface px-2.5 py-1 text-[9px] font-semibold text-clinic-muted transition hover:border-clinic-primary/40 hover:text-clinic-text"
    >
      {label} {value}
    </button>
  );
}

function StatusCount({
  tone,
  value,
  label,
}: {
  tone: 'green' | 'blue' | 'orange' | 'slate';
  value: number;
  label: string;
}) {
  return <StatusBadge tone={tone}>{`${value} ${label}`}</StatusBadge>;
}

function SummaryCard({
  icon: Icon,
  value,
  label,
}: {
  icon: LucideIcon;
  value: string;
  label: string;
}) {
  return (
    <section className="flex min-h-[64px] items-center gap-3 rounded-xl border border-clinic-border bg-clinic-surface px-3 shadow-[0_1px_2px_rgba(4,32,36,0.04)]">
      <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-clinic-primary/10 text-clinic-primary">
        <Icon className="h-4 w-4" />
      </div>
      <div className="min-w-0">
        <p className="truncate text-[16px] font-extrabold leading-5 text-clinic-text">{value}</p>
        <p className="truncate text-[8px] text-clinic-muted">{label}</p>
      </div>
    </section>
  );
}

function PatientContact({ patient }: { patient: DemoPaciente }) {
  return (
    <div className="flex items-center gap-2.5">
      <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-clinic-primary/20 text-[10px] font-extrabold text-clinic-primary">
        {patientInitials(patient.nome)}
      </div>
      <div className="min-w-0">
        <p className="truncate text-[10px] font-extrabold text-clinic-text">{patient.nome}</p>
        <p className="mt-0.5 flex items-center gap-1 truncate text-[8px] text-clinic-muted">
          <Mail className="h-2.5 w-2.5 shrink-0" />
          {patient.email}
        </p>
      </div>
    </div>
  );
}

function PatientTags({ tags }: { tags: string[] }) {
  return (
    <div className="flex items-center gap-1 overflow-hidden">
      {tags.slice(0, 2).map((tag) => (
        <span
          key={tag}
          className={`whitespace-nowrap rounded-full px-2 py-0.5 text-[8px] font-bold ${
            tagTones[tag] ?? 'bg-clinic-muted/10 text-clinic-muted'
          }`}
        >
          {tag}
        </span>
      ))}
      {tags.length > 2 ? (
        <span className="rounded-full bg-clinic-muted/10 px-2 py-0.5 text-[8px] font-bold text-clinic-muted">
          +{tags.length - 2}
        </span>
      ) : null}
    </div>
  );
}

function Attendant({ patient }: { patient: DemoPaciente }) {
  return (
    <div className="flex items-center gap-2">
      <span
        className={`flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-[8px] font-extrabold ${
          avatarTones[patient.atendenteTone]
        }`}
      >
        {patient.atendenteIniciais}
      </span>
      <span className="truncate text-[9px] font-semibold text-clinic-text">{patient.atendente}</span>
    </div>
  );
}

function patientInitials(name: string) {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('');
}

function patientStatusTone(status: DemoPaciente['status']) {
  if (status === 'Em Atendimento') return 'green';
  if (status === 'Agendado') return 'blue';
  if (status === 'Follow UP') return 'orange';
  return 'slate';
}
