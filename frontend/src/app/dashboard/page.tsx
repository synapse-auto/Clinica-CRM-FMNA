import Link from 'next/link';
import {
  Calendar as CalendarIcon,
  User,
  Users,
  MessageSquare,
  Activity,
  AlertCircle,
  Clock,
  TrendingUp,
  LineChart,
  BarChart3,
  PieChart,
  Stethoscope,
  type LucideIcon,
} from 'lucide-react';
import { getClinicaAtual, getDashboardData } from '@/services/backend';
import type { DashboardPeriodo, DashboardResponse } from '@/types/dashboard';

type DashboardPageProps = {
  searchParams?: Promise<{
    periodo?: string;
    data?: string;
  }>;
};

const PERIODOS: Array<{ label: string; value: DashboardPeriodo }> = [
  { label: 'Dia', value: 'DIA' },
  { label: 'Semana', value: 'SEMANA' },
  { label: 'Mês', value: 'MES' },
];

export default async function DashboardPage({ searchParams }: DashboardPageProps) {
  const params = (await searchParams) ?? {};
  const periodo = normalizePeriodo(params.periodo);
  const data = normalizeDate(params.data);
  const [clinica, dashboard] = await Promise.all([
    getClinicaAtual(),
    getDashboardData(periodo, data),
  ]);

  const agendaLabel = clinica.usaCirurgiasNaAgenda
    ? 'Consultas Agendadas'
    : 'Exames Agendados';
  const agendaSubtitle = clinica.tipoClinica === 'ULTRASSONOGRAFIA'
    ? 'ultrassons e exames'
    : 'consultas e retornos';

  return (
    <div className="flex-1 overflow-auto bg-slate-50 p-8">
      <div className="mb-8 flex flex-col justify-between gap-4 md:flex-row md:items-center">
        <div>
          <h1 className="text-3xl font-bold tracking-tight text-slate-900">
            Dashboard {clinica.nome}
          </h1>
          <p className="mt-1 text-sm text-slate-500">{formatDisplayDate(data)}</p>
        </div>

        <div className="flex items-center gap-3">
          <div className="flex rounded-lg border border-slate-200 bg-white p-1 shadow-sm">
            {PERIODOS.map((item) => (
              <Link
                key={item.value}
                href={`/dashboard?periodo=${item.value}&data=${data}`}
                className={`rounded-md px-4 py-1.5 text-sm font-medium transition-colors ${
                  periodo === item.value
                    ? 'bg-teal-600 text-white shadow-sm'
                    : 'text-slate-600 hover:bg-slate-50'
                }`}
              >
                {item.label}
              </Link>
            ))}
          </div>

          <div className="flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-4 py-2 text-sm font-medium text-slate-700 shadow-sm">
            {formatShortDate(data)}
            <CalendarIcon className="h-4 w-4 text-slate-400" />
          </div>
        </div>
      </div>

      <div className="mb-6 grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6">
        <KpiCard
          title="Equipe Online"
          value={String(dashboard.equipeOnline)}
          subtitle={`de ${dashboard.equipeTotal} usuários ativos`}
          trend={dashboard.equipeOnline > 0 ? 'em operação' : 'sem presença'}
          icon={User}
          iconColor="text-teal-500"
          iconBg="bg-teal-50"
        />
        <KpiCard
          title="Novos Pacientes"
          value={String(dashboard.novosPacientes)}
          subtitle={periodoLabel(periodo)}
          trend="dados internos"
          icon={Users}
          iconColor="text-blue-500"
          iconBg="bg-blue-50"
        />
        <KpiCard
          title="Mensagens"
          value={String(dashboard.totalMensagens)}
          subtitle={periodoLabel(periodo)}
          trend="WhatsApp oficial"
          icon={MessageSquare}
          iconColor="text-purple-500"
          iconBg="bg-purple-50"
        />
        <KpiCard
          title={agendaLabel}
          value={String(dashboard.consultasAgendadas)}
          subtitle={agendaSubtitle}
          trend={clinica.usaCirurgiasNaAgenda ? 'inclui cirurgias' : 'sem cirurgias'}
          icon={clinica.tipoClinica === 'ULTRASSONOGRAFIA' ? Stethoscope : Activity}
          iconColor="text-emerald-500"
          iconBg="bg-emerald-50"
        />
        <KpiCard
          title="Confirmações Pendentes"
          value={String(dashboard.confirmacoesPendentes)}
          subtitle="aguardando resposta"
          trend="agenda interna"
          icon={AlertCircle}
          iconColor="text-orange-500"
          iconBg="bg-orange-50"
        />
        <KpiCard
          title="Tempo Médio"
          value={dashboard.tempoMedioResposta}
          subtitle="de resposta"
          trend="mensagens internas"
          icon={Clock}
          iconColor="text-teal-500"
          iconBg="bg-teal-50"
        />
      </div>

      <div className="mb-6 grid grid-cols-1 gap-6 lg:grid-cols-3">
        <section className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm lg:col-span-2">
          <div className="mb-6 flex items-start justify-between">
            <div>
              <h2 className="text-lg font-bold text-slate-800">Pico de Mensagens</h2>
              <p className="text-sm text-slate-500">Mensagens por hora no período</p>
            </div>
            <LineChart className="h-5 w-5 text-teal-600" />
          </div>
          <HourlyBars dashboard={dashboard} />
        </section>

        <section className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <div className="mb-6 flex items-start justify-between">
            <div>
              <h2 className="text-lg font-bold text-slate-800">Pacientes</h2>
              <p className="text-sm text-slate-500">Movimentação no período</p>
            </div>
            <BarChart3 className="h-5 w-5 text-teal-600" />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <MetricTile title="Novos" value={dashboard.novosPacientes} subtitle="primeiro contato" />
            <MetricTile title="Fidelização" value={`${dashboard.taxaFidelizacao.toFixed(1)}%`} subtitle="recorrência" />
            <MetricTile title="Agendados" value={dashboard.consultasAgendadas} subtitle={agendaSubtitle} />
            <MetricTile title="Follow-up" value={clinica.followUpAutomatico ? 'Ativo' : 'Manual'} subtitle="configuração" />
          </div>
        </section>
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <section className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm lg:col-span-2">
          <div className="mb-6 flex items-start justify-between">
            <div>
              <h2 className="text-lg font-bold text-slate-800">Agendamentos</h2>
              <p className="text-sm text-slate-500">
                {clinica.usaCirurgiasNaAgenda ? 'Consultas, cirurgias e exames por dia' : 'Exames e ultrassons por dia'}
              </p>
            </div>
          </div>
          <DailyBars dashboard={dashboard} />
        </section>

        <section className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <div className="mb-6 flex items-start justify-between">
            <div>
              <h2 className="text-lg font-bold text-slate-800">Distribuição de Serviços</h2>
              <p className="text-sm text-slate-500">Baseada na agenda interna</p>
            </div>
            <PieChart className="h-5 w-5 text-teal-600" />
          </div>

          <div className="space-y-3">
            {dashboard.distribuicaoServicos.length > 0 ? (
              dashboard.distribuicaoServicos.map((item) => (
                <LegendItem
                  key={item.servico}
                  color="bg-teal-600"
                  label={item.servico}
                  value={`${item.percentual.toFixed(1)}%`}
                />
              ))
            ) : (
              <p className="text-sm text-slate-500">Sem serviços no período.</p>
            )}
          </div>
        </section>
      </div>
    </div>
  );
}

function HourlyBars({ dashboard }: { dashboard: DashboardResponse }) {
  const max = Math.max(...dashboard.picoMensagensPorHora.map((item) => item.total), 1);
  const items = dashboard.picoMensagensPorHora.length > 0
    ? dashboard.picoMensagensPorHora
    : Array.from({ length: 6 }, (_, index) => ({ hora: index * 4, total: 0 }));

  return (
    <div className="flex h-[250px] items-end gap-3 border-b border-slate-100 pb-2">
      {items.map((item) => (
        <div key={item.hora} className="flex h-full flex-1 flex-col justify-end gap-2">
          <div
            className="min-h-2 rounded-t-sm bg-teal-600 transition-colors"
            style={{ height: `${Math.max((item.total / max) * 100, item.total > 0 ? 8 : 2)}%` }}
          />
          <span className="text-center text-xs text-slate-400">{String(item.hora).padStart(2, '0')}h</span>
        </div>
      ))}
    </div>
  );
}

function DailyBars({ dashboard }: { dashboard: DashboardResponse }) {
  const max = Math.max(...dashboard.agendamentosSemana.map((item) => item.total), 1);
  const items = dashboard.agendamentosSemana.length > 0
    ? dashboard.agendamentosSemana
    : [];

  if (items.length === 0) {
    return (
      <div className="flex h-[200px] items-center justify-center rounded-lg border border-dashed border-slate-200 text-sm text-slate-500">
        Sem agendamentos no período.
      </div>
    );
  }

  return (
    <div className="flex h-[200px] items-end gap-3 border-b border-slate-100 px-4 pb-2">
      {items.map((item) => (
        <div key={item.data} className="flex h-full flex-1 flex-col justify-end gap-2">
          <div
            className="min-h-2 rounded-t-sm bg-indigo-500 transition-colors"
            style={{ height: `${Math.max((item.total / max) * 100, 8)}%` }}
          />
          <span className="text-center text-xs font-medium text-slate-500">
            {formatWeekday(item.data)}
          </span>
        </div>
      ))}
    </div>
  );
}

type KpiCardProps = {
  title: string;
  value: string;
  subtitle: string;
  trend: string;
  icon: LucideIcon;
  iconColor: string;
  iconBg: string;
};

function KpiCard({ title, value, subtitle, trend, icon: Icon, iconColor, iconBg }: KpiCardProps) {
  return (
    <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm transition-shadow hover:shadow-md">
      <div className="mb-4 flex items-start justify-between gap-3">
        <div className={`flex h-10 w-10 items-center justify-center rounded-full ${iconBg}`}>
          <Icon className={`h-5 w-5 ${iconColor}`} />
        </div>
        <div className="flex items-center gap-1 text-xs font-semibold text-teal-600">
          <TrendingUp className="h-3 w-3" />
          <span className="truncate">{trend}</span>
        </div>
      </div>
      <h2 className="text-3xl font-extrabold tracking-tight text-slate-800">{value}</h2>
      <p className="mt-1 text-sm font-semibold text-slate-700">{title}</p>
      <p className="mt-0.5 text-xs text-slate-400">{subtitle}</p>
    </section>
  );
}

type MetricTileProps = {
  title: string;
  value: number | string;
  subtitle: string;
};

function MetricTile({ title, value, subtitle }: MetricTileProps) {
  return (
    <div className="rounded-lg border border-slate-100 bg-slate-50 p-4">
      <h3 className="text-2xl font-bold leading-none text-slate-800">{value}</h3>
      <p className="mt-2 text-sm font-semibold text-slate-700">{title}</p>
      <p className="mt-1 text-[10px] uppercase tracking-wide text-slate-400">{subtitle}</p>
    </div>
  );
}

type LegendItemProps = {
  color: string;
  label: string;
  value: string;
};

function LegendItem({ color, label, value }: LegendItemProps) {
  return (
    <div className="flex items-center justify-between text-sm">
      <div className="flex items-center gap-2">
        <div className={`h-2 w-2 rounded-full ${color}`} />
        <span className="font-medium text-slate-600">{label}</span>
      </div>
      <span className="font-bold text-slate-800">{value}</span>
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
  return 'no dia';
}
