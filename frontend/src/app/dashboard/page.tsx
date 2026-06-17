import Link from 'next/link';
import {
  Activity,
  AlertCircle,
  BarChart3,
  Calendar as CalendarIcon,
  Clock,
  LineChart,
  MessageSquare,
  PieChart,
  Stethoscope,
  User,
  Users,
} from 'lucide-react';
import { DemoCard } from '@/components/demo/DemoCard';
import { MetricCard } from '@/components/demo/MetricCard';
import { PageHeader } from '@/components/demo/PageHeader';
import { SegmentedTabs } from '@/components/demo/SegmentedTabs';
import { getClinicaAtual, getDashboardData } from '@/services/backend';
import type { DashboardPeriodo, DashboardResponse } from '@/types/dashboard';

type DashboardPageProps = {
  searchParams?: Promise<{
    periodo?: string;
    data?: string;
  }>;
};

const periodos: Array<{ label: string; value: DashboardPeriodo }> = [
  { label: 'Dia', value: 'DIA' },
  { label: 'Semanal', value: 'SEMANA' },
  { label: 'Mensal', value: 'MES' },
];

export default async function DashboardPage({ searchParams }: DashboardPageProps) {
  const params = (await searchParams) ?? {};
  const periodo = normalizePeriodo(params.periodo);
  const data = normalizeDate(params.data);
  const [clinica, dashboard] = await Promise.all([
    getClinicaAtual(),
    getDashboardData(periodo, data),
  ]);

  const agendaTitle = clinica.usaCirurgiasNaAgenda ? 'Consultas Agendadas' : 'Exames Agendados';
  const agendaSubtitle = clinica.tipoClinica === 'ULTRASSONOGRAFIA'
    ? 'ultrassons e exames'
    : 'para hoje e amanhã';

  return (
    <div className="h-full overflow-auto p-6 custom-scrollbar">
      <PageHeader
        title="Dashboard"
        description={formatDisplayDate(data)}
        actions={
          <>
            <SegmentedTabs
              items={periodos.map((item) => ({
                label: item.label,
                href: `/dashboard?periodo=${item.value}&data=${data}`,
                active: item.value === periodo,
              }))}
            />
            <Link
              href={`/dashboard?periodo=${periodo}&data=${data}`}
              className="flex h-10 items-center gap-3 rounded-xl border border-clinic-border bg-white px-4 text-sm font-semibold text-clinic-text shadow-sm"
            >
              <CalendarIcon className="h-4 w-4 text-clinic-muted" />
              {formatShortDate(data)}
            </Link>
          </>
        }
      />

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-6">
        <MetricCard
          title="Equipe Online"
          value={dashboard.equipeOnline}
          subtitle={`de ${dashboard.equipeTotal} recepcionistas`}
          trend="+1 hoje"
          icon={User}
          tone="teal"
        />
        <MetricCard
          title="Novos Pacientes"
          value={dashboard.novosPacientes}
          subtitle={periodoLabel(periodo)}
          trend="+33% vs ontem"
          icon={Users}
          tone="blue"
        />
        <MetricCard
          title="Mensagens"
          value={dashboard.totalMensagens}
          subtitle={periodoLabel(periodo)}
          trend="+18% vs ontem"
          icon={MessageSquare}
          tone="purple"
        />
        <MetricCard
          title={agendaTitle}
          value={dashboard.consultasAgendadas}
          subtitle={agendaSubtitle}
          trend={clinica.usaCirurgiasNaAgenda ? 'cirurgias na semana' : 'exames na semana'}
          icon={clinica.tipoClinica === 'ULTRASSONOGRAFIA' ? Stethoscope : Activity}
          tone="cyan"
        />
        <MetricCard
          title="Confirmações Pendentes"
          value={dashboard.confirmacoesPendentes}
          subtitle="aguardando resposta"
          trend="-2 vs ontem"
          icon={AlertCircle}
          tone="orange"
        />
        <MetricCard
          title="Tempo Médio"
          value={dashboard.tempoMedioResposta}
          subtitle="de resposta"
          trend="-0,8min vs ontem"
          icon={Clock}
          tone="teal"
        />
      </div>

      <div className="mt-6 grid grid-cols-1 gap-6 xl:grid-cols-12">
        <DemoCard
          className="xl:col-span-7"
          title="Pico de Mensagens"
          description="Mensagens por hora hoje"
          actions={<LineChart className="h-5 w-5 text-clinic-primary" />}
        >
          <HourlyChart dashboard={dashboard} />
        </DemoCard>

        <DemoCard
          className="xl:col-span-5"
          title="Pacientes da Semana"
          description="Movimentação de pacientes"
          actions={<BarChart3 className="h-5 w-5 text-clinic-primary" />}
        >
          <div className="grid grid-cols-2 gap-3 p-5">
            <SummaryTile color="bg-teal-600" value={dashboard.novosPacientes} label="Novos" caption="primeiro contato" />
            <SummaryTile color="bg-indigo-500" value={Math.round(dashboard.taxaFidelizacao / 8) || 8} label="Recorrentes" caption="retornaram à clínica" />
            <SummaryTile color="bg-blue-500" value={dashboard.consultasAgendadas} label="Agendados" caption="consultas marcadas" />
            <SummaryTile color="bg-orange-500" value={clinica.followUpAutomatico ? 'Ativo' : 'Manual'} label="Follow UP" caption="em acompanhamento" />
          </div>
        </DemoCard>
      </div>

      <div className="mt-6 grid grid-cols-1 gap-6 xl:grid-cols-12">
        <DemoCard
          className="xl:col-span-8"
          title="Agendamentos da Semana"
          description={clinica.usaCirurgiasNaAgenda ? 'Consultas, cirurgias e exames por dia' : 'Consultas e exames por dia'}
          actions={<CalendarIcon className="h-5 w-5 text-clinic-primary" />}
        >
          <DailyChart dashboard={dashboard} />
        </DemoCard>

        <DemoCard
          className="xl:col-span-4"
          title="Distribuição de Serviços"
          description="Interesse dos pacientes"
          actions={<PieChart className="h-5 w-5 text-clinic-primary" />}
        >
          <div className="space-y-4 p-5">
            {dashboard.distribuicaoServicos.length > 0 ? (
              dashboard.distribuicaoServicos.map((item, index) => (
                <div key={item.servico} className="space-y-2">
                  <div className="flex items-center justify-between text-sm">
                    <span className="font-semibold text-clinic-text">{item.servico}</span>
                    <span className="font-bold text-clinic-primary">{item.percentual.toFixed(1)}%</span>
                  </div>
                  <div className="h-2 rounded-full bg-teal-50">
                    <div
                      className={`h-2 rounded-full ${index % 2 === 0 ? 'bg-clinic-primary' : 'bg-indigo-500'}`}
                      style={{ width: `${Math.max(item.percentual, 8)}%` }}
                    />
                  </div>
                </div>
              ))
            ) : (
              <p className="text-sm text-clinic-muted">Sem serviços no período.</p>
            )}
          </div>
        </DemoCard>
      </div>
    </div>
  );
}

function HourlyChart({ dashboard }: { dashboard: DashboardResponse }) {
  const items = dashboard.picoMensagensPorHora.length > 0
    ? dashboard.picoMensagensPorHora
    : Array.from({ length: 6 }, (_, index) => ({ hora: index * 4, total: 0 }));
  const max = Math.max(...items.map((item) => item.total), 1);

  return (
    <div className="p-5">
      <div className="flex h-[250px] items-end gap-3 border-b border-l border-dashed border-clinic-border/80 px-2 pb-2">
        {items.map((item) => (
          <div key={item.hora} className="flex h-full flex-1 flex-col justify-end gap-2">
            <div
              className="min-h-1 rounded-t-md bg-clinic-primary/80"
              style={{ height: `${Math.max((item.total / max) * 100, item.total > 0 ? 8 : 1)}%` }}
            />
            <span className="text-center text-[11px] text-clinic-muted">{String(item.hora).padStart(2, '0')}h</span>
          </div>
        ))}
      </div>
    </div>
  );
}

function DailyChart({ dashboard }: { dashboard: DashboardResponse }) {
  const items = dashboard.agendamentosSemana;
  const max = Math.max(...items.map((item) => item.total), 1);

  return (
    <div className="p-5">
      {items.length > 0 ? (
        <div className="flex h-[220px] items-end gap-4 border-b border-clinic-border/70 px-4 pb-2">
          {items.map((item) => (
            <div key={item.data} className="flex h-full flex-1 flex-col justify-end gap-2">
              <div
                className="min-h-2 rounded-t-lg bg-indigo-500"
                style={{ height: `${Math.max((item.total / max) * 100, 8)}%` }}
              />
              <span className="text-center text-xs font-semibold text-clinic-muted">{formatWeekday(item.data)}</span>
            </div>
          ))}
        </div>
      ) : (
        <div className="flex h-[220px] items-center justify-center rounded-xl border border-dashed border-clinic-border text-sm text-clinic-muted">
          Sem agendamentos no período.
        </div>
      )}
    </div>
  );
}

type SummaryTileProps = {
  color: string;
  value: number | string;
  label: string;
  caption: string;
};

function SummaryTile({ color, value, label, caption }: SummaryTileProps) {
  return (
    <div className="rounded-xl border border-clinic-border bg-teal-50/30 p-4">
      <div className={`mb-5 h-2 w-2 rounded-full ${color}`} />
      <p className="text-2xl font-extrabold text-clinic-primary">{value}</p>
      <p className="mt-1 text-sm font-bold text-clinic-text">{label}</p>
      <p className="mt-1 text-xs text-clinic-muted">{caption}</p>
    </div>
  );
}

function normalizePeriodo(value: string | undefined): DashboardPeriodo {
  if (value === 'SEMANA' || value === 'MES') {
    return value;
  }
  return 'DIA';
}

function normalizeDate(value: string | undefined): string {
  if (value && /^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return value;
  }
  return new Date().toISOString().slice(0, 10);
}

function formatDisplayDate(value: string): string {
  return new Intl.DateTimeFormat('pt-BR', {
    weekday: 'long',
    day: '2-digit',
    month: 'long',
    year: 'numeric',
    timeZone: 'America/Sao_Paulo',
  }).format(new Date(`${value}T12:00:00-03:00`));
}

function formatShortDate(value: string): string {
  return new Intl.DateTimeFormat('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    timeZone: 'America/Sao_Paulo',
  }).format(new Date(`${value}T12:00:00-03:00`));
}

function formatWeekday(value: string): string {
  return new Intl.DateTimeFormat('pt-BR', {
    weekday: 'short',
    day: '2-digit',
    timeZone: 'America/Sao_Paulo',
  }).format(new Date(`${value}T12:00:00-03:00`));
}

function periodoLabel(periodo: DashboardPeriodo): string {
  if (periodo === 'SEMANA') {
    return 'na semana';
  }
  if (periodo === 'MES') {
    return 'no mês';
  }
  return 'hoje';
}
