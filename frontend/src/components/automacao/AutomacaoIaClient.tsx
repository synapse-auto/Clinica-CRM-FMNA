'use client';

import { type FormEvent, type ReactNode, useState } from 'react';
import {
  AlertCircle,
  Bot,
  CalendarCheck,
  Edit3,
  Heart,
  MessageCircle,
  Plus,
  Settings2,
  Star,
  X,
} from 'lucide-react';
import { DemoCard } from '@/components/demo/DemoCard';
import { EmptyState } from '@/components/demo/EmptyState';
import { PageHeader } from '@/components/demo/PageHeader';
import { StatusBadge } from '@/components/demo/StatusBadge';
import { FormSelect } from '@/components/ui/form-select';
import { Switch } from '@/components/ui/switch';
import type {
  ConsultaLembreteConfig,
  ConsultaLembreteConfigPayload,
  FollowUpConfig,
  FollowUpConfigPayload,
  FollowUpTemporary,
  MensagemFestivaConfig,
  MensagemFestivaConfigPayload,
} from '@/types/automacao';
import type { ClinicaAtualResponse } from '@/types/dashboard';
import { getResponseMessage, isApiErrorBody, safeJson } from '@/components/operacional/client-helpers';

type AutomacaoKind = 'follow-up' | 'lembrete' | 'festiva';

type AutomacaoIaClientProps = {
  initialFollowUps: FollowUpConfig[];
  initialLembretes: ConsultaLembreteConfig[];
  initialFestivas: MensagemFestivaConfig[];
  initialFila: FollowUpTemporary[];
  clinic: ClinicaAtualResponse;
  initialError: string | null;
};

type EditingItem =
  | { kind: 'follow-up'; item: FollowUpConfig | null }
  | { kind: 'lembrete'; item: ConsultaLembreteConfig | null }
  | { kind: 'festiva'; item: MensagemFestivaConfig | null };

export function AutomacaoIaClient({
  initialFollowUps,
  initialLembretes,
  initialFestivas,
  initialFila,
  clinic,
  initialError,
}: AutomacaoIaClientProps) {
  const [followUps, setFollowUps] = useState(initialFollowUps);
  const [lembretes, setLembretes] = useState(initialLembretes);
  const [festivas, setFestivas] = useState(initialFestivas);
  const [editing, setEditing] = useState<EditingItem | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const automacoesAtivas = [
    ...followUps.map((item) => item.ativo),
    ...lembretes.map((item) => item.ativo),
    ...festivas.map((item) => item.ativo),
  ].filter(Boolean).length;

  function openCreate(kind: AutomacaoKind) {
    setSubmitError(null);
    setEditing({ kind, item: null } as EditingItem);
  }

  function openEdit(item: FollowUpConfig | ConsultaLembreteConfig | MensagemFestivaConfig, kind: AutomacaoKind) {
    setSubmitError(null);
    setEditing({ kind, item } as EditingItem);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!editing) return;

    setSubmitError(null);
    setIsSubmitting(true);

    const form = new FormData(event.currentTarget);
    const endpoint = endpointFor(editing);
    const payload = payloadFor(editing.kind, form);

    try {
      const response = await fetch(endpoint, {
        method: editing.item ? 'PUT' : 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      const body = await safeJson<FollowUpConfig | ConsultaLembreteConfig | MensagemFestivaConfig>(response);

      if (!response.ok || !body || isApiErrorBody(body)) {
        setSubmitError(getResponseMessage(body, 'Não foi possível salvar a automação.'));
        return;
      }

      upsertAutomation(editing.kind, body);
      setEditing(null);
    } catch {
      setSubmitError('Serviço de automação indisponível. Tente novamente em instantes.');
    } finally {
      setIsSubmitting(false);
    }
  }

  async function toggleStatus(kind: AutomacaoKind, item: FollowUpConfig | ConsultaLembreteConfig | MensagemFestivaConfig) {
    const response = await fetch(`${basePath(kind)}/${item.id}/status`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ativo: !item.ativo }),
    });
    const body = await safeJson<FollowUpConfig | ConsultaLembreteConfig | MensagemFestivaConfig>(response);
    if (response.ok && body && !isApiErrorBody(body)) {
      upsertAutomation(kind, body);
    }
  }

  function upsertAutomation(
    kind: AutomacaoKind,
    item: FollowUpConfig | ConsultaLembreteConfig | MensagemFestivaConfig,
  ) {
    if (kind === 'follow-up') {
      setFollowUps((current) => upsert(current, item as FollowUpConfig));
    } else if (kind === 'lembrete') {
      setLembretes((current) => upsert(current, item as ConsultaLembreteConfig));
    } else {
      setFestivas((current) => upsert(current, item as MensagemFestivaConfig));
    }
  }

  const n8nStatus = clinic.usaN8n
    ? (clinic.n8nWebhookConfigurado ? 'Automação configurada' : 'Automação sem webhook')
    : 'Automação desativada';

  return (
    <>
      <PageHeader
        icon={<Bot className="h-4 w-4" />}
        title="Automação"
        description="Configure confirmações de consultas, follow-ups e fidelização de pacientes"
      />

      {initialError ? <ErrorMessage message={initialError} /> : null}

      <div className="mb-3 grid grid-cols-1 gap-2 md:grid-cols-2 xl:grid-cols-4">
        <AutomationMetric icon={Settings2} label="Automações ativas" value={automacoesAtivas} />
        <AutomationMetric icon={MessageCircle} label="Itens na fila" value={initialFila.length} />
        <AutomationMetric icon={Heart} label="Campanhas festivas" value={festivas.length} />
        <AutomationMetric icon={Bot} label="Integração de automação" value={n8nStatus} />
      </div>

      <div className="grid grid-cols-1 gap-3 xl:grid-cols-2">
        <AutomationList
          title="Follow-ups"
          description="Relacionamento contínuo com pacientes"
          icon={<Heart className="h-4 w-4" />}
          emptyText="Nenhum follow-up configurado."
          buttonLabel="Novo follow-up"
          items={followUps}
          kind="follow-up"
          onCreate={() => openCreate('follow-up')}
          onEdit={(item) => openEdit(item, 'follow-up')}
          onToggle={(item) => void toggleStatus('follow-up', item)}
          describeItem={(item) => `${item.delayQuantidade ?? 1} ${formatUnit(item.delayUnidade ?? 'DIAS')} · ${item.horarioEnvio ?? '09:00'}`}
        />

        <AutomationList
          title="Lembretes de consulta"
          description="Confirmações antes de consultas e exames"
          icon={<CalendarCheck className="h-4 w-4" />}
          emptyText="Nenhum lembrete de consulta configurado."
          buttonLabel="Novo lembrete"
          items={lembretes}
          kind="lembrete"
          onCreate={() => openCreate('lembrete')}
          onEdit={(item) => openEdit(item, 'lembrete')}
          onToggle={(item) => void toggleStatus('lembrete', item)}
          describeItem={(item) => `${item.antecedenciaQuantidade} ${formatUnit(item.antecedenciaUnidade)} · ${item.horarioEnvio ?? '08:00'}`}
        />
      </div>

      <div className="mt-3 grid grid-cols-1 gap-3 xl:grid-cols-2">
        <AutomationList
          title="Mensagens festivas"
          description="Templates por feriado ou data comemorativa"
          icon={<Star className="h-4 w-4" />}
          emptyText="Nenhuma campanha festiva configurada."
          buttonLabel="Nova mensagem festiva"
          items={festivas}
          kind="festiva"
          onCreate={() => openCreate('festiva')}
          onEdit={(item) => openEdit(item, 'festiva')}
          onToggle={(item) => void toggleStatus('festiva', item)}
          describeItem={(item) => `${item.mesDia} · ${item.canal}`}
        />

        <DemoCard
          title="Fila temporária de follow-ups"
          description="Itens preparados para processamento interno"
          icon={<MessageCircle className="h-4 w-4" />}
        >
          <div className="space-y-2 px-4 pb-4">
            {initialFila.length > 0 ? (
              initialFila.map((item) => (
                <div key={item.id} className="flex items-center justify-between gap-3 rounded-lg border border-clinic-border bg-clinic-surface-muted px-3 py-2">
                  <span className="text-[10px] font-bold text-clinic-text">{item.titulo}</span>
                  <StatusBadge tone={item.status === 'PENDENTE' ? 'orange' : 'teal'}>{item.status}</StatusBadge>
                </div>
              ))
            ) : (
              <EmptyState
                icon={MessageCircle}
                title="Fila vazia"
                description="Nenhum item de follow-up temporário no momento."
              />
            )}
          </div>
        </DemoCard>
      </div>

      {editing ? (
        <AutomationDialog
          editing={editing}
          error={submitError}
          isSubmitting={isSubmitting}
          onClose={() => setEditing(null)}
          onSubmit={handleSubmit}
        />
      ) : null}
    </>
  );
}

function AutomationList<T extends FollowUpConfig | ConsultaLembreteConfig | MensagemFestivaConfig>({
  title,
  description,
  icon,
  emptyText,
  buttonLabel,
  items,
  kind,
  onCreate,
  onEdit,
  onToggle,
  describeItem,
}: {
  title: string;
  description: string;
  icon: ReactNode;
  emptyText: string;
  buttonLabel: string;
  items: T[];
  kind: AutomacaoKind;
  onCreate: () => void;
  onEdit: (item: T) => void;
  onToggle: (item: T) => void;
  describeItem: (item: T) => string;
}) {
  return (
    <DemoCard
      title={title}
      description={description}
      icon={icon}
      actions={(
        <button type="button" onClick={onCreate} className="flex h-8 items-center gap-2 rounded-lg bg-clinic-primary px-3 text-[10px] font-bold text-white">
          <Plus className="h-3.5 w-3.5" />
          {buttonLabel}
        </button>
      )}
    >
      <div className="space-y-3 px-4 pb-4">
        {items.length === 0 ? (
          <p className="rounded-lg border border-dashed border-clinic-border bg-clinic-surface-muted px-3 py-6 text-center text-[10px] text-clinic-muted">
            {emptyText}
          </p>
        ) : (
          items.map((item) => (
            <article key={`${kind}-${item.id}`} className="rounded-lg border border-clinic-border bg-clinic-surface-muted p-3">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <h3 className="truncate text-[11px] font-extrabold text-clinic-text">{item.nome}</h3>
                  <p className="mt-0.5 text-[9px] text-clinic-muted">{descriptionFor(item, describeItem)}</p>
                  <p className="mt-1 text-[9px] font-semibold text-clinic-primary">{describeItem(item)}</p>
                </div>
                <StatusBadge tone={item.ativo ? 'green' : 'slate'}>{item.ativo ? 'Ativo' : 'Pausado'}</StatusBadge>
              </div>
              <p className="mt-3 line-clamp-3 text-[10px] leading-5 text-clinic-muted">{messageFor(item)}</p>
              <div className="mt-3 flex gap-2">
                <IconButton label="Editar automação" onClick={() => onEdit(item)} icon={<Edit3 className="h-3.5 w-3.5" />} />
                <Switch
                  checked={item.ativo}
                  onCheckedChange={() => onToggle(item)}
                  aria-label={item.ativo ? 'Pausar automação' : 'Ativar automação'}
                  label=""
                />
              </div>
            </article>
          ))
        )}
      </div>
    </DemoCard>
  );
}

function AutomationDialog({
  editing,
  error,
  isSubmitting,
  onClose,
  onSubmit,
}: {
  editing: EditingItem;
  error: string | null;
  isSubmitting: boolean;
  onClose: () => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/35 p-4">
      <section role="dialog" aria-modal="true" aria-labelledby="automacao-dialog-title" className="max-h-[92vh] w-full max-w-2xl overflow-auto rounded-2xl border border-clinic-border bg-clinic-surface p-5 shadow-xl custom-scrollbar">
        <div className="mb-4 flex items-start justify-between gap-3">
          <h2 id="automacao-dialog-title" className="text-[15px] font-extrabold text-clinic-text">
            {dialogTitle(editing)}
          </h2>
          <button type="button" onClick={onClose} className="flex h-8 w-8 items-center justify-center rounded-lg text-clinic-muted hover:bg-clinic-soft hover:text-clinic-text" aria-label="Fechar">
            <X className="h-4 w-4" />
          </button>
        </div>

        {error ? <ErrorMessage message={error} /> : null}

        <form onSubmit={onSubmit} className="space-y-3">
          <TextField label="Nome" name="nome" defaultValue={editing.item?.nome ?? ''} required />
          <label className="block">
            <span className="mb-1.5 block text-[10px] font-bold text-clinic-text">Descrição</span>
            <textarea name="descricao" defaultValue={descriptionValue(editing.item)} className="h-20 w-full resize-none rounded-lg border border-clinic-border bg-clinic-input p-3 text-sm text-clinic-text outline-none focus:border-clinic-primary" />
          </label>
          {editing.kind === 'follow-up' ? <FollowUpFields item={editing.item as FollowUpConfig | null} /> : null}
          {editing.kind === 'lembrete' ? <LembreteFields item={editing.item as ConsultaLembreteConfig | null} /> : null}
          {editing.kind === 'festiva' ? <FestivaFields item={editing.item as MensagemFestivaConfig | null} /> : null}
          <label className="block">
            <span className="mb-1.5 block text-[10px] font-bold text-clinic-text">Mensagem</span>
            <textarea name="mensagemTemplate" required defaultValue={messageFor(editing.item)} className="h-28 w-full resize-none rounded-lg border border-clinic-border bg-clinic-input p-3 text-sm text-clinic-text outline-none focus:border-clinic-primary" />
          </label>
          <Switch name="ativo" defaultChecked={editing.item?.ativo ?? true} label="Ativo" />
          <div className="flex flex-col-reverse gap-2 pt-2 sm:flex-row sm:justify-end">
            <button type="button" onClick={onClose} className="h-9 rounded-lg border border-clinic-border px-4 text-[10px] font-bold text-clinic-text hover:bg-clinic-soft">Cancelar</button>
            <button type="submit" disabled={isSubmitting} className="h-9 rounded-lg bg-clinic-primary px-4 text-[10px] font-bold text-white disabled:cursor-wait disabled:opacity-70">
              {isSubmitting ? 'Salvando...' : 'Salvar automação'}
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}

function FollowUpFields({ item }: { item: FollowUpConfig | null }) {
  return (
    <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
      <TextField label="Gatilho" name="gatilho" defaultValue={item?.gatilho ?? 'POS_CONSULTA'} required />
      <TextField label="Canal" name="canal" defaultValue={item?.canal ?? 'WHATSAPP'} required />
      <NumberField label="Delay" name="delayQuantidade" defaultValue={item?.delayQuantidade ?? 1} />
      <SelectField label="Unidade" name="delayUnidade" defaultValue={item?.delayUnidade ?? 'DIAS'} options={['MINUTOS', 'HORAS', 'DIAS', 'SEMANAS', 'MESES']} />
      <TimeField label="Horário" name="horarioEnvio" defaultValue={timeValue(item?.horarioEnvio ?? '09:00')} />
    </div>
  );
}

function LembreteFields({ item }: { item: ConsultaLembreteConfig | null }) {
  return (
    <>
      <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
        <TextField label="Canal" name="canal" defaultValue={item?.canal ?? 'WHATSAPP'} required />
        <NumberField label="Antecedência" name="antecedenciaQuantidade" defaultValue={item?.antecedenciaQuantidade ?? 24} />
        <SelectField label="Unidade" name="antecedenciaUnidade" defaultValue={item?.antecedenciaUnidade ?? 'HORAS'} options={['MINUTOS', 'HORAS', 'DIAS', 'SEMANAS']} />
        <TimeField label="Horário" name="horarioEnvio" defaultValue={timeValue(item?.horarioEnvio ?? '08:00')} />
      </div>
      <div className="grid grid-cols-1 gap-2 md:grid-cols-3">
        <CheckboxField label="Permite confirmação" name="permiteConfirmacao" defaultChecked={item?.permiteConfirmacao ?? true} />
        <CheckboxField label="Permite cancelamento" name="permiteCancelamento" defaultChecked={item?.permiteCancelamento ?? true} />
        <CheckboxField label="Permite reagendamento" name="permiteReagendamento" defaultChecked={item?.permiteReagendamento ?? true} />
      </div>
    </>
  );
}

function FestivaFields({ item }: { item: MensagemFestivaConfig | null }) {
  return (
    <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
      <TextField label="Chave" name="chave" defaultValue={item?.chave ?? ''} required />
      <TextField label="Dia MM-DD" name="mesDia" defaultValue={item?.mesDia ?? '12-25'} required />
      <TextField label="Canal" name="canal" defaultValue={item?.canal ?? 'WHATSAPP'} required />
    </div>
  );
}

function TextField({ label, name, defaultValue, required }: { label: string; name: string; defaultValue: string; required?: boolean }) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-[10px] font-bold text-clinic-text">{label}</span>
      <input name={name} defaultValue={defaultValue} required={required} className="h-10 w-full rounded-lg border border-clinic-border bg-clinic-input px-3 text-sm text-clinic-text outline-none focus:border-clinic-primary" />
    </label>
  );
}

function NumberField({ label, name, defaultValue }: { label: string; name: string; defaultValue: number }) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-[10px] font-bold text-clinic-text">{label}</span>
      <input name={name} type="number" min={0} defaultValue={defaultValue} required className="h-10 w-full rounded-lg border border-clinic-border bg-clinic-input px-3 text-sm text-clinic-text outline-none focus:border-clinic-primary" />
    </label>
  );
}

function TimeField({ label, name, defaultValue }: { label: string; name: string; defaultValue: string }) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-[10px] font-bold text-clinic-text">{label}</span>
      <input name={name} type="time" defaultValue={defaultValue} className="h-10 w-full rounded-lg border border-clinic-border bg-clinic-input px-3 text-sm text-clinic-text outline-none focus:border-clinic-primary" />
    </label>
  );
}

function SelectField({ label, name, defaultValue, options }: { label: string; name: string; defaultValue: string; options: string[] }) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-[10px] font-bold text-clinic-text">{label}</span>
      <FormSelect name={name} defaultValue={defaultValue} options={options.map((option) => ({ value: option, label: option }))} />
    </label>
  );
}

function CheckboxField({ label, name, defaultChecked }: { label: string; name: string; defaultChecked: boolean }) {
  return <Switch name={name} defaultChecked={defaultChecked} label={label} className="rounded-lg border border-clinic-border bg-clinic-input px-3 py-2" />;
}

function AutomationMetric({ icon: Icon, label, value }: { icon: typeof Bot; label: string; value: number | string }) {
  return (
    <div className="rounded-xl border border-clinic-border bg-clinic-surface p-3">
      <div className="mb-2 flex h-7 w-7 items-center justify-center rounded-lg bg-clinic-soft text-clinic-primary">
        <Icon className="h-4 w-4" />
      </div>
      <p className="text-[18px] font-extrabold leading-tight text-clinic-text">{value}</p>
      <p className="mt-1.5 text-[9px] font-bold text-clinic-muted">{label}</p>
    </div>
  );
}

function ErrorMessage({ message }: { message: string }) {
  return (
    <p role="alert" className="mb-3 flex items-center gap-2 rounded-lg border border-clinic-danger/30 bg-clinic-danger/10 px-3 py-2 text-[10px] font-semibold text-clinic-danger">
      <AlertCircle className="h-3.5 w-3.5" />
      {message}
    </p>
  );
}

function IconButton({ label, icon, onClick }: { label: string; icon: ReactNode; onClick: () => void }) {
  return (
    <button type="button" aria-label={label} title={label} onClick={onClick} className="flex h-8 w-8 items-center justify-center rounded-lg border border-clinic-border text-clinic-muted hover:bg-clinic-soft hover:text-clinic-text">
      {icon}
    </button>
  );
}

function payloadFor(kind: AutomacaoKind, form: FormData) {
  if (kind === 'follow-up') {
    return {
      nome: text(form, 'nome'),
      descricao: nullableText(form, 'descricao'),
      ativo: checked(form, 'ativo'),
      gatilho: text(form, 'gatilho'),
      canal: text(form, 'canal'),
      delayQuantidade: numberValue(form, 'delayQuantidade'),
      delayUnidade: text(form, 'delayUnidade'),
      horarioEnvio: text(form, 'horarioEnvio'),
      mensagemTemplate: text(form, 'mensagemTemplate'),
      configJson: null,
    } satisfies FollowUpConfigPayload;
  }

  if (kind === 'lembrete') {
    return {
      nome: text(form, 'nome'),
      descricao: nullableText(form, 'descricao'),
      ativo: checked(form, 'ativo'),
      canal: text(form, 'canal'),
      antecedenciaQuantidade: numberValue(form, 'antecedenciaQuantidade'),
      antecedenciaUnidade: text(form, 'antecedenciaUnidade'),
      horarioEnvio: text(form, 'horarioEnvio'),
      permiteConfirmacao: checked(form, 'permiteConfirmacao'),
      permiteCancelamento: checked(form, 'permiteCancelamento'),
      permiteReagendamento: checked(form, 'permiteReagendamento'),
      mensagemTemplate: text(form, 'mensagemTemplate'),
      configJson: null,
    } satisfies ConsultaLembreteConfigPayload;
  }

  return {
    chave: text(form, 'chave'),
    nome: text(form, 'nome'),
    mesDia: text(form, 'mesDia'),
    ativo: checked(form, 'ativo'),
    canal: text(form, 'canal'),
    mensagemTemplate: text(form, 'mensagemTemplate'),
    configJson: null,
  } satisfies MensagemFestivaConfigPayload;
}

function endpointFor(editing: EditingItem) {
  return editing.item ? `${basePath(editing.kind)}/${editing.item.id}` : basePath(editing.kind);
}

function basePath(kind: AutomacaoKind) {
  if (kind === 'follow-up') return '/api/follow-up/config';
  if (kind === 'lembrete') return '/api/consulta-lembrete/config';
  return '/api/mensagens-festivas/config';
}

function upsert<T extends { id: number; nome: string }>(items: T[], item: T) {
  const exists = items.some((current) => current.id === item.id);
  const next = exists
    ? items.map((current) => (current.id === item.id ? item : current))
    : [...items, item];
  return next.sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR'));
}

function text(form: FormData, name: string) {
  return String(form.get(name) ?? '').trim();
}

function nullableText(form: FormData, name: string) {
  const value = text(form, name);
  return value || null;
}

function numberValue(form: FormData, name: string) {
  return Number(form.get(name) ?? 0);
}

function checked(form: FormData, name: string) {
  return form.get(name) === 'on';
}

function messageFor(item: FollowUpConfig | ConsultaLembreteConfig | MensagemFestivaConfig | null) {
  if (!item) return '';
  if ('mensagemTemplate' in item) {
    return item.mensagemTemplate ?? '';
  }
  return '';
}

function descriptionFor<T extends FollowUpConfig | ConsultaLembreteConfig | MensagemFestivaConfig>(
  item: T,
  fallback: (item: T) => string,
) {
  if ('descricao' in item && item.descricao) {
    return item.descricao;
  }
  return fallback(item);
}

function descriptionValue(item: FollowUpConfig | ConsultaLembreteConfig | MensagemFestivaConfig | null) {
  if (item && 'descricao' in item && item.descricao) {
    return item.descricao;
  }
  return '';
}

function formatUnit(value: string) {
  const normalized = value.toLowerCase();
  if (normalized === 'horas') return 'horas';
  if (normalized === 'dias') return 'dias';
  return normalized;
}

function timeValue(value: string) {
  return value.slice(0, 5);
}

function dialogTitle(editing: EditingItem) {
  if (editing.kind === 'follow-up') return editing.item ? 'Editar follow-up' : 'Novo follow-up';
  if (editing.kind === 'lembrete') return editing.item ? 'Editar lembrete' : 'Novo lembrete';
  return editing.item ? 'Editar mensagem festiva' : 'Nova mensagem festiva';
}
