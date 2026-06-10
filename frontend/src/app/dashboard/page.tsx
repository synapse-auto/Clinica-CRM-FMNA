import {
  Calendar as CalendarIcon,
  User,
  Users,
  MessageSquare,
  Activity,
  AlertCircle,
  Clock,
  TrendingUp,
  TrendingDown,
  LineChart,
  BarChart3,
  PieChart,
} from 'lucide-react';

export default function DashboardPage() {
  return (
    <div className="flex-1 overflow-auto bg-slate-50 p-8">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between mb-8 gap-4">
        <div>
          <h1 className="text-3xl font-bold text-slate-900 tracking-tight">Dashboard</h1>
          <p className="text-slate-500 text-sm mt-1">Sexta-Feira, 16 De Abril De 2026</p>
        </div>
        
        <div className="flex items-center gap-3">
          <div className="flex bg-white rounded-lg border border-slate-200 p-1 shadow-sm">
            <button className="px-4 py-1.5 text-sm font-medium bg-teal-600 text-white rounded-md shadow-sm transition-colors">
              Dia
            </button>
            <button className="px-4 py-1.5 text-sm font-medium text-slate-600 hover:bg-slate-50 rounded-md transition-colors">
              Semanal
            </button>
            <button className="px-4 py-1.5 text-sm font-medium text-slate-600 hover:bg-slate-50 rounded-md transition-colors">
              Mensal
            </button>
          </div>
          
          <button className="flex items-center gap-2 bg-white border border-slate-200 shadow-sm px-4 py-2 rounded-lg text-sm font-medium text-slate-700 hover:bg-slate-50 transition-colors">
            16/04/2026
            <CalendarIcon className="w-4 h-4 text-slate-400" />
          </button>
        </div>
      </div>

      {/* KPI Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6 gap-4 mb-6">
        <KpiCard
          title="Equipe Online"
          value="3"
          subtitle="de 5 recepcionistas"
          trend="+1 hoje"
          trendUp={true}
          icon={User}
          iconColor="text-teal-500"
          iconBg="bg-teal-50"
        />
        <KpiCard
          title="Novos Pacientes"
          value="12"
          subtitle="hoje"
          trend="+33% vs ontem"
          trendUp={true}
          icon={Users}
          iconColor="text-blue-500"
          iconBg="bg-blue-50"
        />
        <KpiCard
          title="Mensagens"
          value="547"
          subtitle="hoje"
          trend="+18% vs ontem"
          trendUp={true}
          icon={MessageSquare}
          iconColor="text-purple-500"
          iconBg="bg-purple-50"
        />
        <KpiCard
          title="Consultas Agendadas"
          value="47"
          subtitle="para hoje e amanhã"
          trend="8 cirurgias na semana"
          trendUp={true}
          icon={Activity}
          iconColor="text-emerald-500"
          iconBg="bg-emerald-50"
        />
        <KpiCard
          title="Confirmações Pendentes"
          value="4"
          subtitle="aguardando resposta"
          trend="-2 vs ontem"
          trendUp={false}
          icon={AlertCircle}
          iconColor="text-orange-500"
          iconBg="bg-orange-50"
        />
        <KpiCard
          title="Tempo Médio"
          value="4,2min"
          subtitle="de resposta"
          trend="-0,8min vs ontem"
          trendUp={true}
          icon={Clock}
          iconColor="text-teal-500"
          iconBg="bg-teal-50"
        />
      </div>

      {/* Charts Section 1 */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 mb-6">
        <div className="lg:col-span-2 bg-white rounded-xl border border-slate-200 shadow-sm p-6">
          <div className="flex justify-between items-start mb-6">
            <div>
              <h3 className="text-lg font-bold text-slate-800">Pico de Mensagens</h3>
              <p className="text-sm text-slate-500">Mensagens por hora hoje</p>
            </div>
            <LineChart className="w-5 h-5 text-teal-600" />
          </div>
          {/* Placeholder Gráfico */}
          <div className="h-[250px] w-full flex items-end justify-center pb-4 relative">
            <div className="absolute inset-0 border-b border-l border-slate-100 grid grid-cols-5 grid-rows-4">
              {Array.from({ length: 20 }).map((_, i) => (
                <div key={i} className="border-t border-r border-slate-50/50" />
              ))}
            </div>
            {/* Curva simulada */}
            <div className="w-full h-full relative overflow-hidden flex items-end opacity-80">
              <div className="w-1/3 h-[60%] bg-gradient-to-t from-teal-50 to-blue-200 rounded-tl-full rounded-tr-full shadow-[0_0_15px_rgba(59,130,246,0.3)]"></div>
              <div className="w-1/3 h-[40%] bg-gradient-to-t from-blue-50 to-blue-100 rounded-tl-full rounded-tr-full -ml-8"></div>
              <div className="w-1/3 h-[70%] bg-gradient-to-t from-teal-50 to-teal-200 rounded-tl-full rounded-tr-full -ml-8 shadow-[0_0_15px_rgba(13,148,136,0.3)]"></div>
              <div className="w-1/3 h-[30%] bg-gradient-to-t from-blue-50 to-blue-100 rounded-tl-full -ml-8"></div>
            </div>
            <div className="absolute bottom-0 w-full flex justify-between text-xs text-slate-400 translate-y-6">
              <span>00h</span>
              <span>04h</span>
              <span>08h</span>
              <span>12h</span>
              <span>16h</span>
              <span>20h</span>
            </div>
          </div>
        </div>

        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
          <div className="flex justify-between items-start mb-6">
            <div>
              <h3 className="text-lg font-bold text-slate-800">Pacientes da Semana</h3>
              <p className="text-sm text-slate-500">Movimentação de pacientes</p>
            </div>
            <div className="flex gap-1">
              <button className="p-1.5 border border-slate-200 rounded text-slate-400 hover:bg-slate-50">
                <BarChart3 className="w-4 h-4" />
              </button>
              <button className="p-1.5 border border-slate-200 rounded text-teal-600 bg-teal-50">
                <TrendingUp className="w-4 h-4" />
              </button>
            </div>
          </div>
          
          <div className="grid grid-cols-2 gap-4">
            <SummaryCard title="Novos" value="12" subtitle="primeiro contato" color="bg-teal-500" />
            <SummaryCard title="Recorrentes" value="8" subtitle="retornaram à clínica" color="bg-purple-500" />
            <SummaryCard title="Agendados" value="24" subtitle="consultas marcadas" color="bg-blue-500" />
            <SummaryCard title="Follow UP" value="7" subtitle="em acompanhamento" color="bg-orange-500" />
          </div>
        </div>
      </div>

      {/* Charts Section 2 */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 bg-white rounded-xl border border-slate-200 shadow-sm p-6">
          <div className="flex justify-between items-start mb-6">
            <div>
              <h3 className="text-lg font-bold text-slate-800">Agendamentos da Semana</h3>
              <p className="text-sm text-slate-500">Consultas, cirurgias e exames por dia</p>
            </div>
          </div>
          <div className="h-[200px] flex items-end justify-between px-4 pb-2 relative">
             <div className="absolute inset-0 border-b border-slate-100"></div>
             {/* Barras simuladas */}
             {['Seg 13', 'Ter 19', 'Qua 14', 'Qui 24', 'Sex 11'].map((day, i) => (
               <div key={day} className="flex gap-2 items-end z-10 w-full justify-center group cursor-pointer">
                 <div className="w-6 sm:w-10 bg-teal-600 rounded-t-sm transition-all duration-300 group-hover:bg-teal-500" style={{ height: `${40 + Math.random() * 60}%` }}></div>
                 <div className="w-6 sm:w-10 bg-indigo-500 rounded-t-sm transition-all duration-300 group-hover:bg-indigo-400" style={{ height: `${10 + Math.random() * 30}%` }}></div>
                 <div className="w-6 sm:w-10 bg-sky-400 rounded-t-sm transition-all duration-300 group-hover:bg-sky-300" style={{ height: `${20 + Math.random() * 40}%` }}></div>
                 <div className="absolute bottom-[-24px] text-xs font-medium text-slate-500">{day}</div>
               </div>
             ))}
          </div>
        </div>

        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
          <div className="flex justify-between items-start mb-6">
            <div>
              <h3 className="text-lg font-bold text-slate-800">Distribuição de Serviços</h3>
              <p className="text-sm text-slate-500">Interesse dos pacientes</p>
            </div>
            <PieChart className="w-5 h-5 text-teal-600" />
          </div>
          
          <div className="flex justify-center items-center h-[140px] mb-6">
            {/* Semi-círculo simulado com CSS */}
            <div className="w-32 h-16 border-[16px] border-b-0 border-teal-600 rounded-t-full relative flex justify-center items-end">
               <div className="absolute w-full h-full border-[16px] border-b-0 border-indigo-500 rounded-t-full top-[-16px] left-[-16px] origin-bottom -rotate-45" style={{ clipPath: 'polygon(50% 0%, 100% 0%, 100% 100%, 50% 100%)' }}></div>
            </div>
          </div>

          <div className="space-y-3">
            <LegendItem color="bg-teal-600" label="Pré-natal" value="35%" />
            <LegendItem color="bg-indigo-500" label="Ginecologia" value="28%" />
            <LegendItem color="bg-sky-400" label="Ultrassonografia" value="18%" />
            <LegendItem color="bg-purple-400" label="Cirurgias" value="11%" />
          </div>
        </div>
      </div>
    </div>
  );
}

// Sub-components

function KpiCard({ title, value, subtitle, trend, trendUp, icon: Icon, iconColor, iconBg }: any) {
  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-5 hover:shadow-md transition-shadow">
      <div className="flex justify-between items-start mb-4">
        <div className={`w-10 h-10 rounded-full flex items-center justify-center ${iconBg}`}>
          <Icon className={`w-5 h-5 ${iconColor}`} />
        </div>
        <div className={`flex items-center gap-1 text-xs font-semibold ${trendUp ? 'text-teal-600' : 'text-orange-500'}`}>
          {trendUp ? <TrendingUp className="w-3 h-3" /> : <TrendingDown className="w-3 h-3" />}
          {trend}
        </div>
      </div>
      <div>
        <h2 className="text-3xl font-extrabold text-slate-800 tracking-tight">{value}</h2>
        <p className="text-sm font-semibold text-slate-700 mt-1">{title}</p>
        <p className="text-xs text-slate-400 mt-0.5">{subtitle}</p>
      </div>
    </div>
  );
}

function SummaryCard({ title, value, subtitle, color }: any) {
  return (
    <div className="bg-slate-50 rounded-lg p-4 border border-slate-100">
      <div className="flex items-center gap-2 mb-2">
        <div className={`w-2 h-2 rounded-full ${color}`}></div>
        <h4 className="text-2xl font-bold text-slate-800 leading-none">{value}</h4>
      </div>
      <p className="text-sm font-semibold text-slate-700">{title}</p>
      <p className="text-[10px] text-slate-400 uppercase tracking-wider mt-1">{subtitle}</p>
    </div>
  );
}

function LegendItem({ color, label, value }: any) {
  return (
    <div className="flex justify-between items-center text-sm">
      <div className="flex items-center gap-2">
        <div className={`w-2 h-2 rounded-full ${color}`}></div>
        <span className="text-slate-600 font-medium">{label}</span>
      </div>
      <span className="font-bold text-slate-800">{value}</span>
    </div>
  );
}
