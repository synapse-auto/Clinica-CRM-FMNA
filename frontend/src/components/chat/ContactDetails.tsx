'use client';

import { useState } from 'react';
import { CalendarCheck, Mail, Phone, Plus, User, X } from 'lucide-react';
import type {
  AtendenteOption,
  AtendimentoDetalhe,
} from '@/types/atendimento';
import type { TagOperacional } from '@/types/operacional';

type Props = {
  detail: AtendimentoDetalhe | null;
  atendentes: AtendenteOption[];
  tags: TagOperacional[];
  availableTags: TagOperacional[];
  canManage: boolean;
  busy: boolean;
  onAssume: () => Promise<void>;
  onTransfer: (usuarioId: number) => Promise<void>;
  onReview: (result: 'APROVADO' | 'RECUSADO' | 'PENDENTE') => Promise<void>;
  onAddTag: (tagId: number) => Promise<void>;
  onRemoveTag: (tagId: number) => Promise<void>;
};

export function ContactDetails({
  detail,
  atendentes,
  tags,
  availableTags,
  canManage,
  busy,
  onAssume,
  onTransfer,
  onReview,
  onAddTag,
  onRemoveTag,
}: Props) {
  const [addingTag, setAddingTag] = useState(false);

  if (!detail) {
    return (
      <aside className="flex h-full w-[300px] items-center justify-center border-l border-clinic-border bg-clinic-surface p-5 text-center text-[11px] text-clinic-muted">
        Selecione uma conversa para ver os detalhes.
      </aside>
    );
  }

  const paciente = detail.paciente;
  const initials = paciente.nome.split(/\s+/).slice(0, 2).map((part) => part[0]).join('');
  const linkedTagIds = new Set(tags.map((tag) => tag.id));
  const tagsToAdd = availableTags.filter((tag) => !linkedTagIds.has(tag.id));

  return (
    <aside className="flex h-full w-[300px] shrink-0 flex-col overflow-y-auto border-l border-clinic-border bg-clinic-surface custom-scrollbar">
      <div className="border-b border-clinic-border p-4 text-center">
        <div className="mx-auto mb-2.5 flex h-14 w-14 items-center justify-center rounded-full bg-clinic-primary/15 text-lg font-extrabold text-clinic-primary ring-4 ring-clinic-soft">
          {initials}
        </div>
        <h2 className="text-[14px] font-extrabold text-clinic-text">{paciente.nome}</h2>
        <p className="mt-1 text-[9px] font-semibold text-clinic-muted">{detail.status}</p>

        {paciente.requerRevisao && canManage ? (
          <div className="mt-3 rounded-lg border border-clinic-warning/30 bg-clinic-warning/5 p-2.5">
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

      <div className="space-y-5 p-4">
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
              className="h-8 w-full rounded-lg bg-clinic-primary text-[10px] font-extrabold text-white disabled:opacity-50"
            >
              Assumir atendimento
            </button>
          ) : null}
          {canManage && atendentes.length > 0 ? (
            <label className="block text-[9px] font-bold text-clinic-muted">
              Transferir para
              <select
                value=""
                disabled={busy}
                onChange={(event) => {
                  if (event.target.value) void onTransfer(Number(event.target.value));
                }}
                className="mt-1 h-9 w-full rounded-lg border border-clinic-border bg-clinic-input px-2 text-[10px] text-clinic-text"
              >
                <option value="">Selecione...</option>
                {atendentes.map((atendente) => (
                  <option key={atendente.id} value={atendente.id}>
                    {atendente.nome}
                  </option>
                ))}
              </select>
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
                  className="inline-flex max-w-full items-center gap-1 rounded-full border border-clinic-border bg-clinic-soft px-2 py-1 text-[9px] font-bold text-clinic-text"
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
                className="flex h-8 w-full items-center justify-center gap-1.5 rounded-lg border border-clinic-border text-[10px] font-extrabold text-clinic-text hover:bg-clinic-hover disabled:opacity-50"
              >
                <Plus className="h-3.5 w-3.5" />
                Adicionar tag
              </button>
              {addingTag && tagsToAdd.length > 0 ? (
                <label className="block text-[9px] font-bold text-clinic-muted">
                  Selecionar tag
                  <select
                    value=""
                    aria-label="Selecionar tag para adicionar"
                    disabled={busy}
                    onChange={(event) => {
                      if (!event.target.value) return;
                      void onAddTag(Number(event.target.value));
                      setAddingTag(false);
                    }}
                    className="mt-1 h-9 w-full rounded-lg border border-clinic-border bg-clinic-input px-2 text-[10px] text-clinic-text"
                  >
                    <option value="">Selecione...</option>
                    {tagsToAdd.map((tag) => (
                      <option key={tag.id} value={tag.id}>
                        {tag.nome}
                      </option>
                    ))}
                  </select>
                </label>
              ) : null}
            </div>
          ) : null}
        </Section>
      </div>
    </aside>
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

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section>
      <h3 className="mb-2.5 text-[9px] font-extrabold uppercase text-clinic-muted">{title}</h3>
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
