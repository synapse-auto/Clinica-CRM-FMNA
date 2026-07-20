'use client';

import { type FormEvent, type ReactNode, useState } from 'react';
import { Bot, CalendarCheck, CalendarClock, Check, Mail, PanelRightClose, Phone, Plus, User, X } from 'lucide-react';
import type {
  AtendenteOption,
  AtendimentoDetalhe,
  AtendimentoLembrete,
  NovoAtendimentoLembrete,
} from '@/types/atendimento';
import type { TagOperacional } from '@/types/operacional';
import { ContactAvatar } from './ContactAvatar';
import { DatePicker } from '@/components/ui/date-picker';
import { FormSelect, SearchableSelect } from '@/components/ui/form-select';

type Props = {
  detail: AtendimentoDetalhe | null;
  loading?: boolean;
  atendentes: AtendenteOption[];
  tags: TagOperacional[];
  availableTags: TagOperacional[];
  reminders: AtendimentoLembrete[];
  remindersLoading: boolean;
  remindersError: string | null;
  canManage: boolean;
  busy: boolean;
  onClose: () => void;
  onAssume: () => Promise<void>;
  onActivateIa: () => Promise<void>;
  onTransfer: (usuarioId: number) => Promise<void>;
  onReview: (result: 'APROVADO' | 'RECUSADO' | 'PENDENTE') => Promise<void>;
  onAddTag: (tagId: number) => Promise<void>;
  onRemoveTag: (tagId: number) => Promise<void>;
  onCreateReminder: (lembrete: NovoAtendimentoLembrete) => Promise<void>;
  onConcludeReminder: (lembreteId: number) => Promise<void>;
  onCancelReminder: (lembreteId: number) => Promise<void>;
};

export function ContactDetails({
  detail,
  loading = false,
  atendentes,
  tags,
  availableTags,
  reminders,
  remindersLoading,
  remindersError,
  canManage,
  busy,
  onClose,
  onAssume,
  onActivateIa,
  onTransfer,
  onReview,
  onAddTag,
  onRemoveTag,
  onCreateReminder,
  onConcludeReminder,
  onCancelReminder,
}: Props) {
  const [addingTag, setAddingTag] = useState(false);
  const [reminderDate, setReminderDate] = useState('');
  const [reminderTime, setReminderTime] = useState('');
  const [reminderMessage, setReminderMessage] = useState('');

  if (!detail) {
    return (
      <aside id="atendimento-detalhes" className="relative flex h-full w-[336px] items-center justify-center border-l border-clinic-border bg-clinic-surface p-5 text-center text-[11px] text-clinic-muted">
        <DetailsCloseButton onClose={onClose} />
        {loading ? 'Carregando detalhes…' : 'Selecione uma conversa para ver os detalhes.'}
      </aside>
    );
  }

  const paciente = detail.paciente;
  const linkedTagIds = new Set(tags.map((tag) => tag.id));
  const tagsToAdd = availableTags.filter((tag) => !linkedTagIds.has(tag.id));
  const pendingReminders = reminders.filter((reminder) => reminder.status === 'PENDENTE');

  async function submitReminder(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const mensagem = reminderMessage.trim();
    if (!reminderDate || !reminderTime || !mensagem || busy) return;
    await onCreateReminder({
      data: reminderDate,
      hora: reminderTime,
      mensagem,
    });
    setReminderDate('');
    setReminderTime('');
    setReminderMessage('');
  }

  return (
    <aside id="atendimento-detalhes" aria-label="Detalhes do atendimento" className="flex h-full w-[336px] shrink-0 flex-col overflow-y-auto border-l border-clinic-border bg-clinic-surface custom-scrollbar">
      <div className="relative border-b border-clinic-border px-6 py-6 text-center">
        <DetailsCloseButton onClose={onClose} />
        <ContactAvatar name={paciente.nome} url={paciente.fotoUrl} variant="details" />
        <h2 className="text-[16px] font-extrabold text-clinic-text">{paciente.nome}</h2>
        <p className="mt-1 text-[11px] font-semibold text-clinic-muted">{detail.status}</p>

        {paciente.requerRevisao && canManage ? (
          <div className="mt-4 rounded-xl border border-clinic-warning/30 bg-clinic-warning/5 p-3">
            <p className="mb-2 flex items-center justify-center gap-1 text-[9px] font-extrabold text-clinic-warning">
              <CalendarCheck className="h-3.5 w-3.5" />
              Verificar convênio
            </p>
            <div className="grid grid-cols-3 gap-1">
              <ReviewButton label="Aprovar" disabled={busy} onClick={() => onReview('APROVADO')} />
              <ReviewButton label="Recusar" disabled={busy} onClick={() => onReview('RECUSADO')} />
              <ReviewButton label="Depois" disabled={busy} onClick={() => onReview('PENDENTE')} />
            </div>
          </div>
        ) : null}
      </div>

      <div className="space-y-6 p-6">
        <Section title="Contato">
          <DetailRow icon={Phone} text={paciente.telefone} />
          <DetailRow icon={Mail} text={paciente.email ?? 'E-mail não informado'} />
          <DetailRow
            icon={User}
            text={detail.atendentePrincipal?.nome ?? (detail.tratadoPorIa ? 'IA' : 'Não atribuído')}
          />
        </Section>

        <Section title="Atendimento">
          <p className="text-[10px] text-clinic-muted">
            Origem: <strong className="text-clinic-text">{detail.tratadoPorIa ? 'IA' : 'Humano'}</strong>
          </p>
          {paciente.convenioStatus ? (
            <p className="text-[10px] text-clinic-muted">
              Convênio: <strong className="text-clinic-text">{paciente.convenioStatus}</strong>
            </p>
          ) : null}
          {canManage && !detail.atendentePrincipal ? (
            <button
              disabled={busy}
              onClick={() => void onAssume()}
              className="h-9 w-full rounded-lg bg-clinic-primary text-[11px] font-extrabold text-white disabled:opacity-50"
            >
              Assumir atendimento
            </button>
          ) : null}
          {canManage && !detail.tratadoPorIa ? (
            <button
              type="button"
              disabled={busy}
              onClick={() => void onActivateIa()}
              className="flex h-9 w-full items-center justify-center gap-1.5 rounded-lg border border-clinic-primary/40 bg-clinic-primary/10 text-[11px] font-extrabold text-clinic-primary hover:bg-clinic-primary/15 disabled:opacity-50"
            >
              <Bot className="h-3.5 w-3.5" />
              Voltar para IA
            </button>
          ) : null}
          {canManage && atendentes.length > 0 ? (
            <label className="block text-[9px] font-bold text-clinic-muted">
              Transferir para
              <SearchableSelect
                value=""
                disabled={busy}
                onValueChange={(value) => {
                  if (value) void onTransfer(Number(value));
                }}
                className="mt-1 h-10 w-full rounded-lg border border-clinic-border bg-clinic-input px-3 text-[11px] text-clinic-text"
                placeholder="Selecione..."
                options={atendentes.map((atendente) => ({ value: String(atendente.id), label: atendente.nome }))}
              />
            </label>
          ) : null}
        </Section>

        <Section title="Tags">
          {tags.length === 0 ? (
            <p className="text-[10px] text-clinic-muted">Sem tags neste atendimento.</p>
          ) : (
            <div className="flex flex-wrap gap-1.5">
              {tags.map((tag) => (
                <span
                  key={tag.id}
                  className="inline-flex max-w-full items-center gap-1 rounded-full border border-clinic-border bg-clinic-soft px-2.5 py-1.5 text-[10px] font-bold text-clinic-text"
                >
                  <span className="h-2 w-2 shrink-0 rounded-full" style={{ backgroundColor: tag.cor }} />
                  <span className="truncate">{tag.nome}</span>
                  {canManage ? (
                    <button
                      type="button"
                      aria-label={`Remover tag ${tag.nome}`}
                      disabled={busy}
                      onClick={() => void onRemoveTag(tag.id)}
                      className="text-clinic-muted hover:text-clinic-danger disabled:opacity-50"
                    >
                      <X className="h-3 w-3" />
                    </button>
                  ) : null}
                </span>
              ))}
            </div>
          )}

          {canManage ? (
            <div className="space-y-2">
              <button
                type="button"
                disabled={busy || tagsToAdd.length === 0}
                onClick={() => setAddingTag((current) => !current)}
                className="flex h-9 w-full items-center justify-center gap-1.5 rounded-lg border border-clinic-border text-[11px] font-extrabold text-clinic-text hover:bg-clinic-hover disabled:opacity-50"
              >
                <Plus className="h-3.5 w-3.5" />
                Adicionar tag
              </button>
              {addingTag && tagsToAdd.length > 0 ? (
                <label className="block text-[9px] font-bold text-clinic-muted">
                  Selecionar tag
                  <FormSelect
                    value=""
                    aria-label="Selecionar tag para adicionar"
                    disabled={busy}
                    onValueChange={(value) => {
                      if (!value) return;
                      void onAddTag(Number(value));
                      setAddingTag(false);
                    }}
                    className="mt-1 h-9 w-full rounded-lg border border-clinic-border bg-clinic-input px-2 text-[10px] text-clinic-text"
                    placeholder="Selecione..."
                    options={tagsToAdd.map((tag) => ({ value: String(tag.id), label: tag.nome }))}
                  />
                </label>
              ) : null}
            </div>
          ) : null}
        </Section>

        <Section title="Lembretes">
          {canManage ? (
            <form onSubmit={(event) => void submitReminder(event)} className="space-y-2 rounded-lg border border-clinic-border bg-clinic-soft/40 p-2">
              <div className="grid grid-cols-2 gap-2">
                <label className="block text-[9px] font-bold text-clinic-muted">
                  Data
                  <DatePicker
                    aria-label="Data do lembrete"
                    value={reminderDate}
                    disabled={busy}
                    onValueChange={setReminderDate}
                    className="mt-1 h-8 w-full rounded-lg border border-clinic-border bg-clinic-input px-2 text-[10px] text-clinic-text"
                  />
                </label>
                <label className="block text-[9px] font-bold text-clinic-muted">
                  Hora
                  <input
                    aria-label="Hora do lembrete"
                    type="time"
                    value={reminderTime}
                    disabled={busy}
                    onChange={(event) => setReminderTime(event.target.value)}
                    className="mt-1 h-8 w-full rounded-lg border border-clinic-border bg-clinic-input px-2 text-[10px] text-clinic-text"
                  />
                </label>
              </div>
              <label className="block text-[9px] font-bold text-clinic-muted">
                Mensagem
                <textarea
                  aria-label="Mensagem do lembrete"
                  value={reminderMessage}
                  maxLength={500}
                  disabled={busy}
                  onChange={(event) => setReminderMessage(event.target.value)}
                  className="mt-1 min-h-16 w-full resize-none rounded-lg border border-clinic-border bg-clinic-input px-2 py-2 text-[10px] text-clinic-text outline-none focus:border-clinic-primary"
                />
              </label>
              <button
                type="submit"
                disabled={busy || !reminderDate || !reminderTime || !reminderMessage.trim()}
                className="flex h-8 w-full items-center justify-center gap-1.5 rounded-lg bg-clinic-primary text-[10px] font-extrabold text-white disabled:opacity-50"
              >
                <Plus className="h-3.5 w-3.5" />
                Adicionar lembrete
              </button>
            </form>
          ) : null}

          {remindersError ? (
            <p role="alert" className="rounded-lg border border-clinic-danger/30 bg-clinic-danger/10 p-2 text-[10px] font-semibold text-clinic-danger">
              {remindersError}
            </p>
          ) : null}

          {remindersLoading ? (
            <p className="text-[10px] text-clinic-muted">Carregando lembretes...</p>
          ) : pendingReminders.length === 0 ? (
            <p className="text-[10px] text-clinic-muted">Nenhum lembrete para este atendimento.</p>
          ) : (
            <div className="space-y-2">
              {pendingReminders.map((reminder) => (
                <article
                  key={reminder.id}
                  className="rounded-lg border border-clinic-border bg-clinic-surface p-2"
                >
                  <div className="mb-1.5 flex items-center gap-1.5 text-[9px] font-extrabold text-clinic-primary">
                    <CalendarClock className="h-3.5 w-3.5" />
                    {formatReminderDate(reminder.lembrarEm)}
                  </div>
                  <p className="break-words text-[10px] leading-4 text-clinic-text">
                    {reminder.mensagem}
                  </p>
                  {canManage ? (
                    <div className="mt-2 flex gap-1.5">
                      <button
                        type="button"
                        aria-label="Concluir lembrete"
                        disabled={busy}
                        onClick={() => void onConcludeReminder(reminder.id)}
                        className="flex h-7 flex-1 items-center justify-center gap-1 rounded-md border border-clinic-border text-[9px] font-bold text-clinic-text hover:bg-clinic-hover disabled:opacity-50"
                      >
                        <Check className="h-3 w-3" />
                        Concluir
                      </button>
                      <button
                        type="button"
                        aria-label="Cancelar lembrete"
                        disabled={busy}
                        onClick={() => void onCancelReminder(reminder.id)}
                        className="flex h-7 flex-1 items-center justify-center gap-1 rounded-md border border-clinic-border text-[9px] font-bold text-clinic-muted hover:bg-clinic-hover hover:text-clinic-danger disabled:opacity-50"
                      >
                        <X className="h-3 w-3" />
                        Cancelar
                      </button>
                    </div>
                  ) : null}
                </article>
              ))}
            </div>
          )}
        </Section>
      </div>
    </aside>
  );
}

function DetailsCloseButton({ onClose }: { onClose: () => void }) {
  return (
    <button
      type="button"
      aria-label="Minimizar detalhes do atendimento"
      aria-controls="atendimento-detalhes"
      aria-expanded="true"
      title="Minimizar detalhes do atendimento"
      onClick={onClose}
      className="absolute right-3 top-3 flex h-9 w-9 items-center justify-center rounded-lg text-clinic-muted transition hover:bg-clinic-hover hover:text-clinic-text focus-visible:outline-2 focus-visible:outline-clinic-primary"
    >
      <PanelRightClose className="h-4 w-4" />
    </button>
  );
}

function ReviewButton({
  label,
  disabled,
  onClick,
}: {
  label: string;
  disabled: boolean;
  onClick: () => Promise<void>;
}) {
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={() => void onClick()}
      className="rounded-md border border-clinic-border bg-clinic-surface px-1 py-1.5 text-[8px] font-bold text-clinic-text hover:bg-clinic-hover disabled:opacity-50"
    >
      {label}
    </button>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="border-t border-clinic-border pt-5 first:border-t-0 first:pt-0">
      <h3 className="mb-3 text-[10px] font-extrabold uppercase tracking-[0.1em] text-clinic-muted">{title}</h3>
      <div className="space-y-2">{children}</div>
    </section>
  );
}

function DetailRow({
  icon: Icon,
  text,
}: {
  icon: typeof Phone;
  text: string;
}) {
  return (
    <div className="flex items-center gap-2.5 text-[10px] text-clinic-text">
      <Icon className="h-3.5 w-3.5 shrink-0 text-clinic-muted" />
      <span className="truncate">{text}</span>
    </div>
  );
}

function formatReminderDate(value: string) {
  return new Intl.DateTimeFormat('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value));
}
