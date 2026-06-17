import { Bot, CalendarCheck, Heart, MessageCircle, Save, Settings2, Star, ToggleLeft, ToggleRight } from 'lucide-react';
import { DemoCard } from '@/components/demo/DemoCard';
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
    <div className="h-full overflow-auto p-6 custom-scrollbar">
      <PageHeader
        icon={<Bot className="h-5 w-5" />}
        title="Automação"
        description="Configure confirmações de consultas, follow ups e fidelização de pacientes"
        actions={
          <button className="flex h-10 items-center gap-2 rounded-xl bg-clinic-primary px-4 text-sm font-bold text-white shadow-sm">
            <Save className="h-4 w-4" />
            Salvar ajustes
          </button>
        }
      />

      <div className="mb-6 grid grid-cols-1 gap-4 md:grid-cols-3">
        <AutomationMetric icon={Settings2} label="Automações ativas" value={automacoesAtivas} />
        <AutomationMetric icon={MessageCircle} label="Itens na fila N8N" value={fila.length} />
        <AutomationMetric icon={Heart} label="Campanhas festivas" value={festivas.length} />
      </div>

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
        <DemoCard
          title="Confirmação de Consultas"
          description="Lembretes automáticos enviados antes de consultas e exames"
          icon={<CalendarCheck className="h-5 w-5" />}
        >
          <div className="space-y-4 p-5">
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
          icon={<Heart className="h-5 w-5" />}
        >
          <div className="space-y-4 p-5">
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

      <div className="mt-6 grid grid-cols-1 gap-6 xl:grid-cols-2">
        <DemoCard
          title="Mensagens Festivas"
          description="Templates por feriado ou data comemorativa"
          icon={<Star className="h-5 w-5" />}
        >
          <div className="p-5">
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
          description="Itens preparados para consulta/processamento pelo N8N"
          icon={<MessageCircle className="h-5 w-5" />}
        >
          <div className="p-5">
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
    </div>
  );
}

type AutomationMetricProps = {
  icon: typeof Bot;
  label: string;
  value: number;
};

function AutomationMetric({ icon: Icon, label, value }: AutomationMetricProps) {
  return (
    <div className="rounded-xl border border-clinic-border bg-white p-5 shadow-sm">
      <div className="mb-4 flex h-10 w-10 items-center justify-center rounded-xl bg-teal-50 text-clinic-primary">
        <Icon className="h-5 w-5" />
      </div>
      <p className="text-3xl font-extrabold text-clinic-text">{value}</p>
      <p className="mt-1 text-sm font-bold text-clinic-muted">{label}</p>
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
    <article className="rounded-xl border border-clinic-border bg-white p-4">
      <div className="mb-4 flex items-start justify-between gap-3">
        <div>
          <h3 className="font-extrabold text-clinic-text">{title}</h3>
          <p className="mt-0.5 text-xs text-clinic-muted">{description}</p>
        </div>
        <button aria-label={active ? 'Desativar automação' : 'Ativar automação'} className="text-clinic-primary">
          {active ? <ToggleRight className="h-7 w-7" /> : <ToggleLeft className="h-7 w-7 text-clinic-muted" />}
        </button>
      </div>
      <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
        <Field label={primaryLabel} value={primaryValue} />
        <Field label={secondaryLabel} value={secondaryValue} />
      </div>
      <label className="mt-4 block">
        <span className="mb-2 block text-xs font-bold text-clinic-muted">Mensagem</span>
        <textarea
          defaultValue={message}
          className="h-24 w-full resize-none rounded-lg border border-clinic-border bg-teal-50/35 p-3 text-sm leading-6 outline-none focus:border-clinic-primary"
        />
      </label>
    </article>
  );
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <label className="block">
      <span className="mb-2 block text-xs font-bold text-clinic-muted">{label}</span>
      <input
        readOnly
        value={value}
        className="h-10 w-full rounded-lg border border-clinic-border bg-teal-50/45 px-3 text-sm font-semibold text-clinic-text outline-none"
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
