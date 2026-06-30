import { redirect } from 'next/navigation';
import {
  AlertCircle,
  Bot,
  CalendarCheck,
  Heart,
  Info,
  MessageCircle,
  Settings2,
  Star,
  ToggleLeft,
  ToggleRight,
} from 'lucide-react';
import { DemoCard } from '@/components/demo/DemoCard';
import { DemoTable } from '@/components/demo/DemoTable';
import { EmptyState } from '@/components/demo/EmptyState';
import { PageHeader } from '@/components/demo/PageHeader';
import { StatusBadge } from '@/components/demo/StatusBadge';
import {
  getConsultaLembreteConfigs,
  getFollowUpConfigs,
  getFollowUpsTemporary,
  getMensagemFestivaConfigs,
  isBackendAuthorizationError,
} from '@/services/backend';
import type {
  ConsultaLembreteConfig,
  FollowUpConfig,
  FollowUpTemporary,
  MensagemFestivaConfig,
} from '@/types/automacao';

export default async function AutomacaoIaPage() {
  let followUps: FollowUpConfig[] = [];
  let lembretes: ConsultaLembreteConfig[] = [];
  let festivas: MensagemFestivaConfig[] = [];
  let fila: FollowUpTemporary[] = [];
  let erroCarregamento: string | null = null;

  try {
    [followUps, lembretes, festivas, fila] = await Promise.all([
      getFollowUpConfigs(),
      getConsultaLembreteConfigs(),
      getMensagemFestivaConfigs(),
      getFollowUpsTemporary(),
    ]);
  } catch (error) {
    if (isBackendAuthorizationError(error)) {
      redirect('/login');
    }
    erroCarregamento =
      'Não foi possível carregar as automações. Verifique a conexão com o servidor.';
  }

  const header = (
    <PageHeader
      icon={<Bot className="h-4 w-4" />}
      title="Automação"
      description="Configure confirmações de consultas, follow-ups e fidelização de pacientes"
    />
  );

  if (erroCarregamento) {
    return (
      <div className="h-full overflow-auto bg-clinic-canvas p-4 custom-scrollbar">
        {header}
        <EmptyState
          icon={AlertCircle}
          title="Automações indisponíveis"
          description={erroCarregamento}
        />
      </div>
    );
  }

  const automacoesAtivas = [
    ...followUps.map((item) => item.ativo),
    ...lembretes.map((item) => item.ativo),
    ...festivas.map((item) => item.ativo),
  ].filter(Boolean).length;

  return (
    <div className="h-full overflow-auto bg-clinic-canvas p-4 custom-scrollbar">
      {header}

      <div className="mb-3 flex items-start gap-2 rounded-lg border border-clinic-border bg-clinic-surface-muted px-3 py-2 text-[10px] text-clinic-muted">
        <Info className="mt-0.5 h-3.5 w-3.5 shrink-0 text-clinic-primary" />
        <span>
          Visualização somente leitura. A edição e o disparo das automações serão habilitados em
          uma próxima etapa.
        </span>
      </div>

      <div className="mb-3 grid grid-cols-1 gap-2 md:grid-cols-2 xl:grid-cols-4">
        <AutomationMetric icon={Settings2} label="Automações ativas" value={automacoesAtivas} />
        <AutomationMetric icon={MessageCircle} label="Itens na fila" value={fila.length} />
        <AutomationMetric icon={Heart} label="Campanhas festivas" value={festivas.length} />
        <AutomationMetric icon={CalendarCheck} label="Lembretes configurados" value={lembretes.length} />
      </div>

      <div className="grid grid-cols-1 gap-3 xl:grid-cols-2">
        <DemoCard
          title="Confirmação de Consultas"
          description="Lembretes automáticos enviados antes de consultas e exames"
          icon={<CalendarCheck className="h-4 w-4" />}
        >
          <div className="space-y-3 px-4 pb-4">
            {lembretes.length > 0 ? (
              lembretes.map((item) => (
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
              ))
            ) : (
              <ConfigPlaceholder text="Nenhum lembrete de consulta configurado." />
            )}
          </div>
        </DemoCard>

        <DemoCard
          title="Fidelização de Pacientes"
          description="Relacionamento contínuo com follow-ups automáticos"
          icon={<Heart className="h-4 w-4" />}
        >
          <div className="space-y-3 px-4 pb-4">
            {followUps.length > 0 ? (
              followUps.map((item) => (
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
              ))
            ) : (
              <ConfigPlaceholder text="Nenhum follow-up configurado." />
            )}
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
            {festivas.length > 0 ? (
              <DemoTable
                data={festivas}
                getKey={(item) => item.id}
                columns={[
                  { key: 'nome', label: 'Data', render: (item) => <span className="font-bold text-clinic-text">{item.nome}</span> },
                  { key: 'mesDia', label: 'Dia', render: (item) => item.mesDia },
                  { key: 'status', label: 'Status', render: (item) => <StatusBadge tone={item.ativo ? 'green' : 'slate'}>{item.ativo ? 'Ativo' : 'Pausado'}</StatusBadge> },
                ]}
              />
            ) : (
              <ConfigPlaceholder text="Nenhuma campanha festiva configurada." />
            )}
          </div>
        </DemoCard>

        <DemoCard
          title="Fila temporária de follow-ups"
          description="Itens preparados para processamento interno"
          icon={<MessageCircle className="h-4 w-4" />}
        >
          <div className="px-4 pb-4">
            {fila.length > 0 ? (
              <DemoTable
                data={fila}
                getKey={(item) => item.id}
                columns={[
                  { key: 'titulo', label: 'Follow-up', render: (item) => <span className="font-bold text-clinic-text">{item.titulo}</span> },
                  { key: 'status', label: 'Status', render: (item) => <StatusBadge tone={item.status === 'PENDENTE' ? 'orange' : 'teal'}>{item.status}</StatusBadge> },
                ]}
              />
            ) : (
              <ConfigPlaceholder text="Nenhum item na fila no momento." />
            )}
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

function ConfigPlaceholder({ text }: { text: string }) {
  return (
    <p className="rounded-lg border border-dashed border-clinic-border bg-clinic-surface-muted px-3 py-6 text-center text-[10px] text-clinic-muted">
      {text}
    </p>
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
        <span
          className={`flex items-center gap-1 text-[9px] font-bold ${active ? 'text-clinic-primary' : 'text-clinic-muted'}`}
          aria-label={active ? 'Automação ativa' : 'Automação pausada'}
        >
          {active ? <ToggleRight className="h-6 w-6" /> : <ToggleLeft className="h-6 w-6" />}
          {active ? 'Ativo' : 'Pausado'}
        </span>
      </div>
      <div className="grid grid-cols-1 gap-2 md:grid-cols-2">
        <Field label={primaryLabel} value={primaryValue} />
        <Field label={secondaryLabel} value={secondaryValue} />
      </div>
      <label className="mt-3 block">
        <span className="mb-1.5 block text-[9px] font-bold text-clinic-muted">Mensagem</span>
        <textarea
          readOnly
          value={message}
          className="h-20 w-full resize-none rounded-lg border border-clinic-border bg-clinic-input p-3 text-[10px] leading-5 text-clinic-text outline-none"
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
