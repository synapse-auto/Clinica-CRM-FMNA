import { redirect } from 'next/navigation';
import {
  Activity,
  AlertCircle,
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
import { EmptyState } from '@/components/demo/EmptyState';
import { GroupedBarChart } from '@/components/demo/GroupedBarChart';
import { LineAreaChart } from '@/components/demo/LineAreaChart';
import { MetricCard } from '@/components/demo/MetricCard';
import { PageHeader } from '@/components/demo/PageHeader';
import { DashboardPeriodFilter } from '@/components/dashboard/DashboardPeriodFilter';
import { DashboardPeriodLabel } from '@/components/dashboard/DashboardPeriodLabel';
import {
  getDashboardPeriod,
  getSaoPauloDate,
  normalizeDashboardFilter,
} from '@/lib/dashboard-period';
import { normalizeServiceName } from '@/lib/service-distribution';
import {
  getClinicaAtual,
  getDashboardData,
  isBackendAuthorizationError,
} from '@/services/backend';
import type {
  ClinicaAtualResponse,
  DashboardResponse,
} from '@/types/dashboard';

type DashboardPageProps = {
  searchParams?: Promise<{
    periodo?: string;
    data?: string;
  }>;
};

export default async function DashboardPage({ searchParams }: DashboardPageProps) {
  const params = (await searchParams) ?? {};
  const today = getSaoPauloDate();
  const filter = normalizeDashboardFilter(params.periodo, params.data, today);
  const period = getDashboardPeriod(filter, today);

  let clinica: ClinicaAtualResponse | null = null;
  let dashboard: DashboardResponse | null = null;
  let erroCarregamento: string | null = null;

  try {
    [clinica, dashboard] = await Promise.all([
      getClinicaAtual(),
      getDashboardData(period.backendPeriod, period.data),
    ]);
  } catch (error) {
    if (isBackendAuthorizationError(error)) {
      redirect('/login');
    }
    erroCarregamento =
      'Não foi possível carregar os indicadores. Verifique a conexão com o servidor.';
  }

  const header = (
    <PageHeader
      title="Dashboard"
      description="Indicadores operacionais da clínica"
      actions={
        <DashboardPeriodFilter selected={filter} today={today} />
      }
    />
  );

  if (erroCarregamento || !dashboard || !clinica) {
    return (
      <div className="h-full overflow-auto bg-clinic-canvas p-4 custom-scrollbar">
        {header}
        <EmptyState
          icon={AlertCircle}
          title="Indicadores indisponíveis"
          description={
            erroCarregamento ??
            'Não foi possível carregar o painel no momento. Tente novamente em alguns instantes.'
          }
        />
      </div>
    );
  }

  const agendaTitle = clinica.usaCirurgiasNaAgenda ? 'Consultas Agendadas' : 'Exames Agendados';
  const agendaSubtitle = clinica.tipoClinica === 'ULTRASSONOGRAFIA'
    ? 'ultrassons e exames'
    : 'para hoje e amanhã';
  const confirmationRate = calculateConfirmationRate(dashboard);
  const serviceItems = buildServiceItems(dashboard);
  const fidelizacao = Math.round(dashboard.taxaFidelizacao);

  return (
    <div className="h-full overflow-auto bg-clinic-canvas p-4 custom-scrollbar">
      {header}
      <DashboardPeriodLabel period={period} />

      <div className="grid grid-cols-1 gap-2 md:grid-cols-2 xl:grid-cols-6">
        <MetricCard
          title="Equipe Online"
          value={dashboard.equipeOnline}
          subtitle={`de ${dashboard.equipeTotal} recepcionistas`}
          icon={User}
          tone="teal"
        />
        <MetricCard
          title="Novos Pacientes"
          value={dashboard.novosPacientes}
          subtitle={periodoLabel(period.backendPeriod)}
          icon={Users}
          tone="blue"
        />
        <MetricCard
          title="Mensagens"
          value={dashboard.totalMensagens}
          subtitle={periodoLabel(period.backendPeriod)}
          icon={MessageSquare}
          tone="purple"
        />
        <MetricCard
          title={agendaTitle}
          value={dashboard.consultasAgendadas}
          subtitle={agendaSubtitle}
          icon={clinica.tipoClinica === 'ULTRASSONOGRAFIA' ? Stethoscope : Activity}
          tone="cyan"
        />
        <MetricCard
          title="Confirmações Pendentes"
          value={dashboard.confirmacoesPendentes}
          subtitle="aguardando resposta"
          icon={AlertCircle}
          tone="orange"
        />
        <MetricCard
          title="Tempo Médio"
          value={dashboard.tempoMedioResposta}
          subtitle="de resposta"
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
          description="Novos pacientes por dia"
          actions={<TrendingUp className="h-4 w-4" />}
        >
          <LineAreaChart
            data={dashboard.pacientesSemana.map((item) => ({
              label: formatWeekday(item.data),
              value: item.total,
            }))}
          />
        </DemoCard>
      </div>

      <div className="mt-3 grid grid-cols-1 gap-3 xl:grid-cols-12">
        <DemoCard
          className="xl:col-span-8"
          title="Agendamentos da Semana"
          description="Total de agendamentos por dia"
          actions={<CalendarDays className="h-4 w-4" />}
        >
          <GroupedBarChart
            labels={dashboard.agendamentosSemana.map((item) => formatWeekday(item.data))}
            series={[
              {
                label: 'Agendamentos',
                color: 'var(--clinic-primary)',
                values: dashboard.agendamentosSemana.map((item) => item.total),
              },
            ]}
          />
        </DemoCard>

        <DemoCard
          className="xl:col-span-4"
          title="Distribuição de Serviços"
          description="Interesse dos pacientes"
          actions={<Activity className="h-4 w-4" />}
        >
          {serviceItems.length > 0 ? (
            <div className="flex min-h-[206px] items-center px-5 pb-4">
              <DonutChart items={serviceItems} />
            </div>
          ) : (
            <div className="flex min-h-[206px] items-center justify-center px-5 pb-4 text-center text-[10px] text-clinic-muted">
              Sem dados de serviços no período.
            </div>
          )}
        </DemoCard>
      </div>

      <div className="mt-3 grid grid-cols-1 gap-3 xl:grid-cols-2">
        <DemoCard
          title="Follow-Up"
          description="Acompanhamento automático de pacientes"
          icon={<RefreshCw className="h-4 w-4" />}
        >
          <div className="px-4 pb-4">
            <EmptyState
              icon={RefreshCw}
              title="Réguas de relacionamento"
              description="Configure follow-ups, lembretes e mensagens festivas na área de Automação."
            />
          </div>
        </DemoCard>

        <DemoCard
          title="Fidelização"
          description="Taxa de retorno dos pacientes"
          icon={<Heart className="h-4 w-4 text-clinic-pink" />}
        >
          <div className="flex min-h-[206px] flex-col items-center justify-center px-5 pb-4 text-center">
            <p className="text-[44px] font-extrabold leading-none text-clinic-pink">
              {fidelizacao}%
            </p>
            <p className="mt-2 text-[11px] font-bold text-clinic-text">Taxa de fidelização</p>
            <p className="mt-1 text-[9px] text-clinic-muted">
              Indicador calculado a partir dos retornos registrados.
            </p>
          </div>
        </DemoCard>
      </div>

      <div className="mt-3 grid grid-cols-1 gap-2 pb-1">
        <CompactMetric icon={CheckCircle2} tone="green" value={`${confirmationRate}%`} label="Taxa de Confirmação" />
      </div>
    </div>
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

function buildServiceItems(dashboard: DashboardResponse) {
  const colors = [
    'var(--clinic-primary)',
    'var(--clinic-blue)',
    'var(--clinic-indigo)',
    'var(--clinic-cyan)',
    'var(--clinic-orange)',
  ];

  return dashboard.distribuicaoServicos.slice(0, 5).map((item, index) => ({
    label: normalizeServiceName({ servicoNome: item.servico }),
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

function formatWeekday(value: string): string {
  const formatted = new Intl.DateTimeFormat('pt-BR', {
    weekday: 'short',
    day: '2-digit',
    timeZone: 'America/Sao_Paulo',
  }).format(new Date(`${value}T12:00:00-03:00`));
  return formatted.replace('.', '');
}

function periodoLabel(_periodo: string): string {
  if (_periodo === 'SEMANA') {
    return 'na semana';
  }
  if (_periodo === 'MES') {
    return 'no mês';
  }
  return 'hoje';
}
