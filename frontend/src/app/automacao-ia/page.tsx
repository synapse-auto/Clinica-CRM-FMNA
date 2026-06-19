import {
  Bot,
  CalendarCheck,
  Heart,
  MessageCircle,
  Save,
  Settings2,
  Sparkles,
  Star,
  ToggleLeft,
  ToggleRight,
} from 'lucide-react';
import { DemoCard } from '@/components/demo/DemoCard';
import { DonutChart } from '@/components/demo/DonutChart';
import { GroupedBarChart } from '@/components/demo/GroupedBarChart';
import { DemoTable } from '@/components/demo/DemoTable';
import { PageHeader } from '@/components/demo/PageHeader';
import { StatusBadge } from '@/components/demo/StatusBadge';
import {
  getConsultaLembreteConfigs,
  getFollowUpConfigs,
  getFollowUpsTemporary,
  getMensagemFestivaConfigs,
} from '@/services/backend';

export default async function AutomacaoIaPage() {
  const [followUps, lembretes, festivas, fila] = await Promise.all([
    getFollowUpConfigs(),
    getConsultaLembreteConfigs(),
    getMensagemFestivaConfigs(),
    getFollowUpsTemporary(),
  ]);

  const automacoesAtivas = [
    ...followUps.map((item) => item.ativo),
    ...lembretes.map((item) => item.ativo),
    ...festivas.map((item) => item.ativo),
  ].filter(Boolean).length;

  return (
    <div className="h-full overflow-auto bg-clinic-canvas p-4 custom-scrollbar">
      <PageHeader
        icon={<Bot className="h-4 w-4" />}
        title="Automação"
        description="Configure confirmações de consultas, follow-ups e fidelização de pacientes"
        actions={
          <button className="flex h-8 items-center gap-2 rounded-lg bg-clinic-primary px-3 text-[10px] font-bold text-white">
            <Save className="h-3.5 w-3.5" />
            Salvar ajustes
          </button>
        }
      />

      <div className="mb-3 grid grid-cols-1 gap-2 md:grid-cols-2 xl:grid-cols-4">
        <AutomationMetric icon={Settings2} label="Automações ativas" value={automacoesAtivas} />
        <AutomationMetric icon={MessageCircle} label="Itens na fila N8N" value={fila.length} />
        <AutomationMetric icon={Heart} label="Campanhas festivas" value={festivas.length} />
        <AutomationMetric icon={Sparkles} label="Taxa de sucesso" value="91%" />
      </div>

      <div className="grid grid-cols-1 gap-3 xl:grid-cols-2">
        <DemoCard
          title="Confirmação de Consultas"
          description="Lembretes automáticos enviados antes de consultas e exames"
          icon={<CalendarCheck className="h-4 w-4" />}
        >
          <div className="space-y-3 px-4 pb-4">
            {lembretes.map((item) => (
              <AutomationEditor
                key={item.id}
                title={item.nome}
                description={item.descricao}
                active={item.ativo}
                primaryLabel="Enviar com antecedência de"
                primaryValue={`${item.antecedenciaQuantidade} ${formatUnit(item.antecedenciaUnidade)}`}
                secondaryLabel="Horário de envio"
                secondaryValue={item.horarioEnvio ?? '08:00'}
                message={item.mensagemTemplate ?? 'Mensagem ainda não configurada.'}
              />
            ))}
          </div>
        </DemoCard>

        <DemoCard
          title="Fidelização de Pacientes"
          description="Relacionamento contínuo com follow-ups automáticos"
          icon={<Heart className="h-4 w-4" />}
        >
          <div className="space-y-3 px-4 pb-4">
            {followUps.map((item) => (
              <AutomationEditor
                key={item.id}
                title={item.nome}
                description={item.descricao}
                active={item.ativo}
                primaryLabel="Quando enviar"
                primaryValue={`${item.delayQuantidade ?? 1} ${formatUnit(item.delayUnidade ?? 'DIAS')}`}
                secondaryLabel="Horário preferencial"
                secondaryValue={item.horarioEnvio ?? '09:00'}
                message={item.mensagemTemplate ?? 'Mensagem ainda não configurada.'}
              />
            ))}
          </div>
        </DemoCard>
      </div>

      <div className="mt-3 grid grid-cols-1 gap-3 xl:grid-cols-2">
        <DemoCard
          title="Mensagens Festivas"
          description="Templates por feriado ou data comemorativa"
          icon={<Star className="h-4 w-4" />}
        >
          <div className="px-4 pb-4">
            <DemoTable
              data={festivas}
              getKey={(item) => item.id}
              columns={[
                { key: 'nome', label: 'Data', render: (item) => <span className="font-bold text-clinic-text">{item.nome}</span> },
                { key: 'mesDia', label: 'Dia', render: (item) => item.mesDia },
                { key: 'status', label: 'Status', render: (item) => <StatusBadge tone={item.ativo ? 'green' : 'slate'}>{item.ativo ? 'Ativo' : 'Pausado'}</StatusBadge> },
              ]}
            />
          </div>
        </DemoCard>

        <DemoCard
          title="Fila temporária de follow-ups"
          description="Itens preparados para consulta e processamento pelo N8N"
          icon={<MessageCircle className="h-4 w-4" />}
        >
          <div className="px-4 pb-4">
            <DemoTable
              data={fila}
              getKey={(item) => item.id}
              columns={[
                { key: 'titulo', label: 'Follow-up', render: (item) => <span className="font-bold text-clinic-text">{item.titulo}</span> },
                { key: 'origem', label: 'Origem', render: (item) => item.origem },
                { key: 'status', label: 'Status', render: (item) => <StatusBadge tone={item.status === 'PENDENTE' ? 'orange' : 'teal'}>{item.status}</StatusBadge> },
              ]}
            />
          </div>
        </DemoCard>
      </div>

      <div className="mt-3 grid grid-cols-1 gap-3 xl:grid-cols-12">
        <DemoCard className="xl:col-span-8" title="Performance das automações" description="Envios, confirmações e conversões nos últimos dias">
          <GroupedBarChart
            height={190}
            labels={['Seg', 'Ter', 'Qua', 'Qui', 'Sex']}
            series={[
              { label: 'Enviadas', color: 'var(--clinic-primary)', values: [42, 56, 49, 63, 58] },
              { label: 'Respondidas', color: 'var(--clinic-blue)', values: [31, 40, 38, 49, 45] },
              { label: 'Convertidas', color: 'var(--clinic-success)', values: [19, 27, 24, 32, 29] },
            ]}
          />
        </DemoCard>
        <DemoCard className="xl:col-span-4" title="Saúde das automações" description="Status dos fluxos ativos">
          <div className="flex min-h-[190px] items-center px-5 pb-4">
            <DonutChart
              compact
              centerLabel="91%"
              items={[
                { label: 'Sucesso', value: 91, color: 'var(--clinic-success)' },
                { label: 'Reprocessando', value: 6, color: 'var(--clinic-orange)' },
                { label: 'Falhas', value: 3, color: 'var(--clinic-danger)' },
              ]}
            />
          </div>
        </DemoCard>
      </div>
    </div>
  );
}

type AutomationMetricProps = {
  icon: typeof Bot;
  label: string;
  value: number | string;
};

function AutomationMetric({ icon: Icon, label, value }: AutomationMetricProps) {
  return (
    <div className="rounded-xl border border-clinic-border bg-clinic-surface p-3">
      <div className="mb-2 flex h-7 w-7 items-center justify-center rounded-lg bg-clinic-soft text-clinic-primary">
        <Icon className="h-4 w-4" />
      </div>
      <p className="text-[20px] font-extrabold leading-none text-clinic-text">{value}</p>
      <p className="mt-1.5 text-[9px] font-bold text-clinic-muted">{label}</p>
    </div>
  );
}

type AutomationEditorProps = {
  title: string;
  description: string;
  active: boolean;
  primaryLabel: string;
  primaryValue: string;
  secondaryLabel: string;
  secondaryValue: string;
  message: string;
};

function AutomationEditor({
  title,
  description,
  active,
  primaryLabel,
  primaryValue,
  secondaryLabel,
  secondaryValue,
  message,
}: AutomationEditorProps) {
  return (
    <article className="rounded-lg border border-clinic-border bg-clinic-surface-muted p-3">
      <div className="mb-3 flex items-start justify-between gap-3">
        <div>
          <h3 className="text-[11px] font-extrabold text-clinic-text">{title}</h3>
          <p className="mt-0.5 text-[9px] text-clinic-muted">{description}</p>
        </div>
        <button aria-label={active ? 'Desativar automação' : 'Ativar automação'} className="text-clinic-primary">
          {active ? <ToggleRight className="h-6 w-6" /> : <ToggleLeft className="h-6 w-6 text-clinic-muted" />}
        </button>
      </div>
      <div className="grid grid-cols-1 gap-2 md:grid-cols-2">
        <Field label={primaryLabel} value={primaryValue} />
        <Field label={secondaryLabel} value={secondaryValue} />
      </div>
      <label className="mt-3 block">
        <span className="mb-1.5 block text-[9px] font-bold text-clinic-muted">Mensagem</span>
        <textarea
          defaultValue={message}
          className="h-20 w-full resize-none rounded-lg border border-clinic-border bg-clinic-input p-3 text-[10px] leading-5 text-clinic-text outline-none focus:border-clinic-primary"
        />
      </label>
    </article>
  );
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-[9px] font-bold text-clinic-muted">{label}</span>
      <input
        readOnly
        value={value}
        className="h-9 w-full rounded-lg border border-clinic-border bg-clinic-input px-3 text-[10px] font-semibold text-clinic-text outline-none"
      />
    </label>
  );
}

function formatUnit(value: string) {
  const normalized = value.toLowerCase();
  if (normalized === 'horas') return 'horas antes';
  if (normalized === 'dias') return 'dias após';
  return normalized;
}
