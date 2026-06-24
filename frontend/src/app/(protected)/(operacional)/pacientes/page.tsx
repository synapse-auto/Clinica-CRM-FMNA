import {
  Activity,
  Mail,
  Phone,
  Users,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { DemoTable } from '@/components/demo/DemoTable';
import { StatusBadge } from '@/components/demo/StatusBadge';
import { getPacientes } from '@/services/backend';
import type { PacienteResumo } from '@/types/paciente';

export default async function PacientesPage() {
  let pacientes: PacienteResumo[] = [];
  let erroCarregamento: string | null = null;

  try {
    pacientes = await getPacientes();
  } catch {
    erroCarregamento = 'Não foi possível carregar os pacientes. Verifique a conexão com o servidor.';
  }

  const statusCount = contarStatus(pacientes);

  return (
    <div className="h-full overflow-auto bg-clinic-canvas p-4 custom-scrollbar">
      <header className="mb-3 border-b border-clinic-border pb-3">
        <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
          <div>
            <h1 className="text-[17px] font-extrabold leading-6 text-clinic-text">Pacientes</h1>
            <p className="text-[10px] text-clinic-muted">{pacientes.length} pacientes</p>
          </div>
        </div>
      </header>

      {erroCarregamento ? (
        <p role="alert" className="mb-3 rounded-lg border border-clinic-danger/30 bg-clinic-danger/10 px-3 py-2 text-[10px] font-semibold text-clinic-danger">
          {erroCarregamento}
        </p>
      ) : null}

      <div className="mb-3 grid grid-cols-1 gap-3 xl:grid-cols-[minmax(0,1fr)_180px]">
        <section className="flex min-h-[64px] flex-wrap items-center gap-3 rounded-xl border border-clinic-border bg-clinic-surface px-4 py-3 shadow-[0_1px_2px_rgba(4,32,36,0.04)]">
          <div className="flex items-center gap-2 border-r border-clinic-border pr-3 text-[10px] font-bold text-clinic-text">
            <Activity className="h-4 w-4 text-clinic-primary" />
            Status Atual
          </div>
          <StatusBadge tone="green">{`${statusCount.emAtendimento} Em Atendimento`}</StatusBadge>
          <StatusBadge tone="blue">{`${statusCount.agendado} Agendados`}</StatusBadge>
          <StatusBadge tone="orange">{`${statusCount.outros} Outros`}</StatusBadge>
          <StatusBadge tone="slate">{`${statusCount.finalizado} Finalizados`}</StatusBadge>
        </section>

        <SummaryCard
          icon={Users}
          value={pacientes.length.toString()}
          label="Total de Pacientes"
        />
      </div>

      {pacientes.length === 0 && !erroCarregamento ? (
        <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-clinic-border bg-clinic-surface py-16 text-center">
          <Users className="mb-3 h-10 w-10 text-clinic-muted" />
          <p className="text-[12px] font-bold text-clinic-text">Nenhum paciente encontrado</p>
          <p className="mt-1 text-[10px] text-clinic-muted">
            Os pacientes serão importados automaticamente após a sincronização com o sistema clínico.
          </p>
        </div>
      ) : (
        <DemoTable
          data={pacientes}
          getKey={(item) => item.id}
          columns={[
            {
              key: 'contato',
              label: 'Paciente',
              className: 'min-w-[260px] w-[30%]',
              render: (item) => <PatientContact paciente={item} />,
            },
            {
              key: 'status',
              label: 'Status',
              className: 'min-w-[130px] w-[14%]',
              render: (item) => (
                <StatusBadge tone={statusTone(item.status)}>{formatStatus(item.status)}</StatusBadge>
              ),
            },
            {
              key: 'telefone',
              label: 'Telefone',
              className: 'min-w-[160px] w-[18%]',
              render: (item) => (
                <span className="flex items-center gap-1.5 whitespace-nowrap font-semibold text-clinic-text">
                  <Phone className="h-3 w-3 text-clinic-muted" />
                  {item.telefone ?? '—'}
                </span>
              ),
            },
            {
              key: 'origem',
              label: 'Origem',
              className: 'min-w-[110px] w-[12%]',
              render: (item) => (
                <span className="text-[9px] text-clinic-muted">
                  {item.externalSource ?? 'Manual'}
                </span>
              ),
            },
          ]}
        />
      )}
    </div>
  );
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

function PatientContact({ paciente }: { paciente: PacienteResumo }) {
  return (
    <div className="flex items-center gap-2.5">
      <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-clinic-primary/20 text-[10px] font-extrabold text-clinic-primary">
        {iniciais(paciente.nome)}
      </div>
      <div className="min-w-0">
        <p className="truncate text-[10px] font-extrabold text-clinic-text">{paciente.nome}</p>
        <p className="mt-0.5 flex items-center gap-1 truncate text-[8px] text-clinic-muted">
          <Mail className="h-2.5 w-2.5 shrink-0" />
          {paciente.externalId ? `ID ${paciente.externalId}` : 'Sem ID externo'}
        </p>
      </div>
    </div>
  );
}

function iniciais(nome: string) {
  return nome
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('');
}

function formatStatus(status: string) {
  const map: Record<string, string> = {
    EM_ATENDIMENTO: 'Em Atendimento',
    AGENDADO: 'Agendado',
    FINALIZADO: 'Finalizado',
    INATIVO: 'Inativo',
  };
  return map[status] ?? status;
}

function statusTone(status: string): 'green' | 'blue' | 'orange' | 'slate' {
  if (status === 'EM_ATENDIMENTO') return 'green';
  if (status === 'AGENDADO') return 'blue';
  if (status === 'FINALIZADO') return 'slate';
  return 'orange';
}

function contarStatus(pacientes: PacienteResumo[]) {
  return pacientes.reduce(
    (acc, p) => {
      if (p.status === 'EM_ATENDIMENTO') acc.emAtendimento++;
      else if (p.status === 'AGENDADO') acc.agendado++;
      else if (p.status === 'FINALIZADO') acc.finalizado++;
      else acc.outros++;
      return acc;
    },
    { emAtendimento: 0, agendado: 0, finalizado: 0, outros: 0 },
  );
}
