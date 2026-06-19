import Link from 'next/link';
import {
  Activity,
  AlertCircle,
  Calendar as CalendarIcon,
  CalendarDays,
  CheckCircle2,
  Clock,
  Heart,
  LineChart,
  MessageSquare,
  RefreshCw,
  Stethoscope,
  TrendingUp,
  User,
  Users,
} from 'lucide-react';
import { DemoCard } from '@/components/demo/DemoCard';
import { DonutChart } from '@/components/demo/DonutChart';
import { GroupedBarChart } from '@/components/demo/GroupedBarChart';
import { LineAreaChart } from '@/components/demo/LineAreaChart';
import { MetricCard } from '@/components/demo/MetricCard';
import { PageHeader } from '@/components/demo/PageHeader';
import { SegmentedTabs } from '@/components/demo/SegmentedTabs';
import { demoDashboardVisual } from '@/mocks/demoDashboard';
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
  const confirmationRate = calculateConfirmationRate(dashboard);
  const appointmentChart = buildAppointmentChart(dashboard, clinica.usaCirurgiasNaAgenda);
  const serviceItems = buildServiceItems(dashboard);

  return (
    <div className="h-full overflow-auto bg-clinic-canvas p-4 custom-scrollbar">
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
              className="flex h-8 items-center gap-2 rounded-lg border border-clinic-border bg-clinic-surface px-3 text-[10px] font-semibold text-clinic-text"
            >
              <CalendarIcon className="h-3.5 w-3.5 text-clinic-muted" />
              {formatShortDate(data)}
            </Link>
          </>
        }
      />

      <div className="grid grid-cols-1 gap-2 md:grid-cols-2 xl:grid-cols-6">
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
          trend={clinica.usaCirurgiasNaAgenda ? '+8 cirurgias na semana' : '+8 exames na semana'}
          icon={clinica.tipoClinica === 'ULTRASSONOGRAFIA' ? Stethoscope : Activity}
          tone="cyan"
        />
        <MetricCard
          title="Confirmações Pendentes"
          value={dashboard.confirmacoesPendentes}
          subtitle="aguardando resposta"
          trend="-2 vs ontem"
          trendDirection="down"
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

      <div className="mt-3 grid grid-cols-1 gap-3 xl:grid-cols-12">
        <DemoCard
          className="xl:col-span-7"
          title="Pico de Mensagens"
          description="Mensagens por hora hoje"
          actions={<LineChart className="h-4 w-4" />}
        >
          <LineAreaChart
            data={dashboard.picoMensagensPorHora.map((item) => ({
              label: `${String(item.hora).padStart(2, '0')}h`,
              value: item.total,
            }))}
          />
        </DemoCard>

        <DemoCard
          className="xl:col-span-5"
          title="Pacientes da Semana"
          description="Movimentação de pacientes"
          actions={<TrendingUp className="h-4 w-4" />}
        >
          <div className="grid grid-cols-2 gap-2 px-4 pb-4 pt-1">
            <SummaryTile tone="teal" value={dashboard.novosPacientes} label="Novos" caption="primeiro contato" />
            <SummaryTile tone="purple" value={demoDashboardVisual.pacientesRecorrentes} label="Recorrentes" caption="retornaram à clínica" />
            <SummaryTile tone="blue" value={dashboard.consultasAgendadas} label="Agendados" caption="consultas marcadas" />
            <SummaryTile tone="orange" value={demoDashboardVisual.followUpsEmAcompanhamento} label="Follow UP" caption="em acompanhamento" />
          </div>
        </DemoCard>
      </div>

      <div className="mt-3 grid grid-cols-1 gap-3 xl:grid-cols-12">
        <DemoCard
          className="xl:col-span-8"
          title="Agendamentos da Semana"
          description={clinica.usaCirurgiasNaAgenda ? 'Consultas, cirurgias e exames por dia' : 'Consultas e exames por dia'}
          actions={<CalendarDays className="h-4 w-4" />}
        >
          <GroupedBarChart labels={appointmentChart.labels} series={appointmentChart.series} />
        </DemoCard>

        <DemoCard
          className="xl:col-span-4"
          title="Distribuição de Serviços"
          description="Interesse dos pacientes"
          actions={<Activity className="h-4 w-4" />}
        >
          <div className="flex min-h-[206px] items-center px-5 pb-4">
            <DonutChart items={serviceItems} />
          </div>
        </DemoCard>
      </div>

      <div className="mt-3 grid grid-cols-1 gap-3 xl:grid-cols-2">
        <DemoCard
          title="Follow-Up"
          description={`${demoDashboardVisual.followUp.programados} programados no período`}
          icon={<RefreshCw className="h-4 w-4" />}
          actions={<PerformancePill tone="green" label={`${demoDashboardVisual.followUp.conversao}% conversão`} />}
        >
          <div className="px-5 pb-4 pt-1">
            <DonutChart
              compact
              centerLabel={`${demoDashboardVisual.followUp.conversao}%`}
              items={[
                { label: 'Enviados', value: demoDashboardVisual.followUp.enviados, color: 'var(--clinic-blue)' },
                { label: 'Convertidos', value: demoDashboardVisual.followUp.convertidos, color: 'var(--clinic-success)' },
                { label: 'Perdidos (5 dias)', value: demoDashboardVisual.followUp.perdidos, color: 'var(--clinic-danger)' },
              ]}
            />
          </div>
        </DemoCard>

        <DemoCard
          title="Fidelização"
          description={`${demoDashboardVisual.fidelizacao.clientes} clientes fidelizados`}
          icon={<Heart className="h-4 w-4 text-clinic-pink" />}
          actions={<PerformancePill tone="pink" label={`${Math.round(dashboard.taxaFidelizacao)}% retorno`} />}
        >
          <div className="px-5 pb-4 pt-1">
            <DonutChart
              compact
              centerLabel={`${Math.round(dashboard.taxaFidelizacao)}%`}
              items={[
                { label: 'Retornos', value: demoDashboardVisual.fidelizacao.retornos, color: 'var(--clinic-blue)' },
                { label: 'Indicações', value: demoDashboardVisual.fidelizacao.indicacoes, color: 'var(--clinic-indigo)' },
                { label: 'Sem retorno', value: demoDashboardVisual.fidelizacao.semRetorno, color: 'var(--clinic-muted)' },
              ]}
            />
            <div className="mt-1 flex items-center justify-between border-t border-clinic-border pt-2 text-[10px] text-clinic-muted">
              <span>NPS médio</span>
              <strong className="text-clinic-blue">{demoDashboardVisual.fidelizacao.nps.toFixed(1).replace('.', ',')}</strong>
            </div>
          </div>
        </DemoCard>
      </div>

      <div className="mt-3 grid grid-cols-1 gap-2 pb-1 md:grid-cols-2">
        <CompactMetric icon={CheckCircle2} tone="green" value={`${confirmationRate}%`} label="Taxa de Confirmação" />
        <CompactMetric icon={Users} tone="blue" value={demoDashboardVisual.pacientesMes} label="Pacientes do Mês" />
      </div>
    </div>
  );
}

type SummaryTileProps = {
  tone: 'teal' | 'purple' | 'blue' | 'orange';
  value: number | string;
  label: string;
  caption: string;
};

const summaryTones = {
  teal: 'bg-clinic-primary text-clinic-primary',
  purple: 'bg-clinic-indigo text-clinic-indigo',
  blue: 'bg-clinic-blue text-clinic-blue',
  orange: 'bg-clinic-orange text-clinic-orange',
};

function SummaryTile({ tone, value, label, caption }: SummaryTileProps) {
  return (
    <div className="rounded-lg border border-clinic-border bg-clinic-surface-muted p-3">
      <div className={`mb-2 h-1.5 w-1.5 rounded-full ${summaryTones[tone].split(' ')[0]}`} />
      <p className={`text-[18px] font-extrabold leading-none ${summaryTones[tone].split(' ')[1]}`}>{value}</p>
      <p className="mt-1.5 text-[10px] font-bold text-clinic-text">{label}</p>
      <p className="text-[8px] text-clinic-muted">{caption}</p>
    </div>
  );
}

function PerformancePill({ label, tone }: { label: string; tone: 'green' | 'pink' }) {
  return (
    <span className={`rounded-md px-2 py-1 text-[9px] font-bold ${
      tone === 'green'
        ? 'bg-clinic-success/10 text-clinic-success'
        : 'bg-clinic-pink/10 text-clinic-pink'
    }`}>
      {label}
    </span>
  );
}

const compactMetricTones = {
  green: 'text-clinic-success',
  blue: 'text-clinic-blue',
  teal: 'text-clinic-primary',
  orange: 'text-clinic-orange',
};

function CompactMetric({
  icon: Icon,
  tone,
  value,
  label,
}: {
  icon: typeof Clock;
  tone: keyof typeof compactMetricTones;
  value: string | number;
  label: string;
}) {
  return (
    <div className="flex min-h-[48px] items-center gap-3 rounded-xl border border-clinic-border bg-clinic-surface px-3 py-2">
      <Icon className={`h-5 w-5 shrink-0 ${compactMetricTones[tone]}`} />
      <div>
        <p className="text-[14px] font-extrabold leading-4 text-clinic-text">{value}</p>
        <p className="text-[8px] text-clinic-muted">{label}</p>
      </div>
    </div>
  );
}

function buildAppointmentChart(dashboard: DashboardResponse, includeSurgeries: boolean) {
  const labels = dashboard.agendamentosSemana.map((item) => formatWeekday(item.data));
  const totals = dashboard.agendamentosSemana.map((item) => item.total);

  return {
    labels,
    series: [
      {
        label: 'Consultas',
        color: 'var(--clinic-primary)',
        values: totals.map((total) => Math.max(1, Math.round(total * 0.62))),
      },
      ...(includeSurgeries
        ? [{
            label: 'Cirurgias',
            color: 'var(--clinic-indigo)',
            values: totals.map((total) => Math.round(total * 0.12)),
          }]
        : []),
      {
        label: 'Exames',
        color: 'var(--clinic-cyan)',
        values: totals.map((total) => Math.max(1, Math.round(total * 0.36))),
      },
    ],
  };
}

function buildServiceItems(dashboard: DashboardResponse) {
  const colors = [
    'var(--clinic-primary)',
    'var(--clinic-blue)',
    'var(--clinic-indigo)',
    'var(--clinic-cyan)',
    'var(--clinic-orange)',
  ];

  return dashboard.distribuicaoServicos.slice(0, 5).map((item, index) => ({
    label: item.servico,
    value: item.total || item.percentual,
    color: colors[index % colors.length],
  }));
}

function calculateConfirmationRate(dashboard: DashboardResponse) {
  if (dashboard.consultasAgendadas <= 0) {
    return 0;
  }

  return Math.max(
    0,
    Math.min(100, Math.round(((dashboard.consultasAgendadas - dashboard.confirmacoesPendentes) / dashboard.consultasAgendadas) * 100)),
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
  const formatted = new Intl.DateTimeFormat('pt-BR', {
    weekday: 'short',
    day: '2-digit',
    timeZone: 'America/Sao_Paulo',
  }).format(new Date(`${value}T12:00:00-03:00`));
  return formatted.replace('.', '');
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
