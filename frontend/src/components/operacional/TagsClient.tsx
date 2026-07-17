'use client';

import { type FormEvent, type ReactNode, useState } from 'react';
import { AlertCircle, Edit3, Plus, Tag, Trash2, X } from 'lucide-react';
import { EmptyState } from '@/components/demo/EmptyState';
import { PageHeader } from '@/components/demo/PageHeader';
import { Switch } from '@/components/ui/switch';
import type { TagOperacional, TagPayload } from '@/types/operacional';
import { getResponseMessage, isApiErrorBody, safeJson } from './client-helpers';

type TagsClientProps = {
  initialTags: TagOperacional[];
  initialError: string | null;
  canManage: boolean;
};

const DEFAULT_COLOR = '#0d9488';
const HEX_COLOR_PATTERN = /^#[0-9a-fA-F]{6}$/;
const COLOR_PALETTE = [
  '#0d9488',
  '#2563eb',
  '#7c3aed',
  '#ef4444',
  '#f97316',
  '#16a34a',
  '#db2777',
  '#64748b',
];

export function TagsClient({ initialTags, initialError, canManage }: TagsClientProps) {
  const [tags, setTags] = useState(initialTags);
  const [editing, setEditing] = useState<TagOperacional | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  function openCreate() {
    setEditing(null);
    setSubmitError(null);
    setIsModalOpen(true);
  }

  function openEdit(tagItem: TagOperacional) {
    setEditing(tagItem);
    setSubmitError(null);
    setIsModalOpen(true);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitError(null);
    setIsSubmitting(true);

    const form = new FormData(event.currentTarget);
    const payload: TagPayload = {
      nome: String(form.get('nome') ?? '').trim(),
      cor: String(form.get('cor') ?? DEFAULT_COLOR).trim() || DEFAULT_COLOR,
      descricao: String(form.get('descricao') ?? '').trim() || null,
      ativo: form.get('ativo') === 'on',
    };

    try {
      const response = await fetch(editing ? `/api/tags/${editing.id}` : '/api/tags', {
        method: editing ? 'PUT' : 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      const body = await safeJson<TagOperacional>(response);

      if (!response.ok || !body || isApiErrorBody(body)) {
        setSubmitError(getResponseMessage(body, 'Não foi possível salvar a tag.'));
        return;
      }

      upsertTag(body);
      setIsModalOpen(false);
    } catch {
      setSubmitError('Serviço de tags indisponível. Tente novamente em instantes.');
    } finally {
      setIsSubmitting(false);
    }
  }

  async function toggleStatus(tagItem: TagOperacional) {
    const response = await fetch(`/api/tags/${tagItem.id}/status`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ativo: !tagItem.ativo }),
    });
    const body = await safeJson<TagOperacional>(response);
    if (response.ok && body && !isApiErrorBody(body)) {
      upsertTag(body);
    }
  }

  async function deleteTag(tagItem: TagOperacional) {
    if (!window.confirm(`Excluir a tag "${tagItem.nome}"?`)) return;
    const response = await fetch(`/api/tags/${tagItem.id}`, { method: 'DELETE' });
    if (response.ok) {
      setTags((current) => current.filter((item) => item.id !== tagItem.id));
    }
  }

  function upsertTag(tagItem: TagOperacional) {
    setTags((current) => {
      const exists = current.some((item) => item.id === tagItem.id);
      const next = exists
        ? current.map((item) => (item.id === tagItem.id ? tagItem : item))
        : [...current, tagItem];
      return next.sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR'));
    });
  }

  return (
    <>
      <PageHeader
        title="Tags"
        description={tags.length === 1 ? '1 tag cadastrada' : `${tags.length} tags cadastradas`}
        actions={canManage ? (
          <button type="button" onClick={openCreate} className="flex h-8 items-center gap-2 rounded-lg bg-clinic-primary px-3 text-[10px] font-bold text-white">
            <Plus className="h-3.5 w-3.5" />
            Nova tag
          </button>
        ) : null}
      />

      {initialError ? <ErrorMessage message={initialError} /> : null}

      {tags.length === 0 && !initialError ? (
        <EmptyState
          icon={Tag}
          title="Nenhuma tag cadastrada"
          description="Crie tags reais para organizar atendimentos e pacientes da UltraMedical."
        />
      ) : (
        <div className="grid grid-cols-1 gap-3 lg:grid-cols-2 xl:grid-cols-3">
          {tags.map((tagItem) => (
            <article key={tagItem.id} className="rounded-xl border border-clinic-border bg-clinic-surface p-4 shadow-[0_1px_2px_rgba(4,32,36,0.04)]">
              <div className="mb-3 flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="h-3 w-3 rounded-full" style={{ backgroundColor: tagItem.cor }} />
                    <h2 className="truncate text-[12px] font-extrabold text-clinic-text">{tagItem.nome}</h2>
                  </div>
                  <p className="mt-1 text-[10px] text-clinic-muted">{tagItem.descricao || 'Sem descrição'}</p>
                </div>
                <StatusPill active={tagItem.ativo} />
              </div>

              {canManage ? (
                <div className="flex flex-wrap gap-2">
                  <IconButton label="Editar tag" onClick={() => openEdit(tagItem)} icon={<Edit3 className="h-3.5 w-3.5" />} />
                  <Switch
                    checked={tagItem.ativo}
                    onCheckedChange={() => void toggleStatus(tagItem)}
                    aria-label={tagItem.ativo ? 'Desativar tag' : 'Ativar tag'}
                    label=""
                  />
                  <IconButton label="Excluir tag" onClick={() => void deleteTag(tagItem)} icon={<Trash2 className="h-3.5 w-3.5" />} danger />
                </div>
              ) : null}
            </article>
          ))}
        </div>
      )}

      {isModalOpen ? (
        <TagDialog
          tagItem={editing}
          error={submitError}
          isSubmitting={isSubmitting}
          onClose={() => setIsModalOpen(false)}
          onSubmit={handleSubmit}
        />
      ) : null}
    </>
  );
}

function TagDialog({
  tagItem,
  error,
  isSubmitting,
  onClose,
  onSubmit,
}: {
  tagItem: TagOperacional | null;
  error: string | null;
  isSubmitting: boolean;
  onClose: () => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}) {
  const [color, setColor] = useState(tagItem?.cor ?? DEFAULT_COLOR);
  const previewColor = HEX_COLOR_PATTERN.test(color) ? color : DEFAULT_COLOR;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/35 p-4">
      <section role="dialog" aria-modal="true" aria-labelledby="tag-dialog-title" className="w-full max-w-lg rounded-2xl border border-clinic-border bg-clinic-surface p-5 shadow-xl">
        <DialogHeader id="tag-dialog-title" title={tagItem ? 'Editar tag' : 'Nova tag'} onClose={onClose} />
        {error ? <ErrorMessage message={error} /> : null}
        <form onSubmit={onSubmit} className="space-y-3">
          <Field label="Nome" name="nome" defaultValue={tagItem?.nome ?? ''} required />
          <ColorField color={color} previewColor={previewColor} onChange={setColor} />
          <label className="block">
            <span className="mb-1.5 block text-[10px] font-bold text-clinic-text">Descrição</span>
            <textarea name="descricao" defaultValue={tagItem?.descricao ?? ''} className="h-20 w-full resize-none rounded-lg border border-clinic-border bg-clinic-input p-3 text-sm text-clinic-text outline-none focus:border-clinic-primary" />
          </label>
          <ActiveCheckbox defaultChecked={tagItem?.ativo ?? true} />
          <DialogActions isSubmitting={isSubmitting} submitLabel="Salvar tag" onClose={onClose} />
        </form>
      </section>
    </div>
  );
}

function ColorField({
  color,
  previewColor,
  onChange,
}: {
  color: string;
  previewColor: string;
  onChange: (color: string) => void;
}) {
  return (
    <div>
      <span className="mb-1.5 block text-[10px] font-bold text-clinic-text">Cor</span>
      <div className="flex items-center gap-2">
        <input
          type="color"
          aria-label="Cor visual"
          value={previewColor}
          onChange={(event) => onChange(event.target.value)}
          className="h-10 w-12 shrink-0 rounded-lg border border-clinic-border bg-clinic-input p-1"
        />
        <input
          name="cor"
          value={color}
          onChange={(event) => onChange(event.target.value)}
          required
          pattern="^#[0-9a-fA-F]{6}$"
          aria-label="Hex da cor"
          className="h-10 min-w-0 flex-1 rounded-lg border border-clinic-border bg-clinic-input px-3 text-sm text-clinic-text outline-none focus:border-clinic-primary"
        />
        <span
          className="inline-flex h-8 shrink-0 items-center rounded-full border border-clinic-border bg-clinic-soft px-2 text-[9px] font-bold text-clinic-text"
          style={{ color: previewColor }}
        >
          Preview
        </span>
      </div>
      <div className="mt-2 flex flex-wrap gap-1.5">
        {COLOR_PALETTE.map((option) => (
          <button
            key={option}
            type="button"
            aria-label={`Usar cor ${option}`}
            onClick={() => onChange(option)}
            className="h-6 w-6 rounded-full border border-clinic-border ring-offset-2 focus:outline-none focus:ring-2 focus:ring-clinic-primary"
            style={{ backgroundColor: option }}
          />
        ))}
      </div>
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

function DialogHeader({ id, title, onClose }: { id: string; title: string; onClose: () => void }) {
  return (
    <div className="mb-4 flex items-start justify-between gap-3">
      <h2 id={id} className="text-[15px] font-extrabold text-clinic-text">{title}</h2>
      <button type="button" onClick={onClose} className="flex h-8 w-8 items-center justify-center rounded-lg text-clinic-muted hover:bg-clinic-soft hover:text-clinic-text" aria-label="Fechar">
        <X className="h-4 w-4" />
      </button>
    </div>
  );
}

function Field({ label, name, defaultValue, required, pattern }: { label: string; name: string; defaultValue: string; required?: boolean; pattern?: string }) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-[10px] font-bold text-clinic-text">{label}</span>
      <input name={name} defaultValue={defaultValue} required={required} pattern={pattern} className="h-10 w-full rounded-lg border border-clinic-border bg-clinic-input px-3 text-sm text-clinic-text outline-none focus:border-clinic-primary" />
    </label>
  );
}

function ActiveCheckbox({ defaultChecked }: { defaultChecked: boolean }) {
  return <Switch name="ativo" defaultChecked={defaultChecked} label="Ativo" />;
}

function DialogActions({ isSubmitting, submitLabel, onClose }: { isSubmitting: boolean; submitLabel: string; onClose: () => void }) {
  return (
    <div className="flex flex-col-reverse gap-2 pt-2 sm:flex-row sm:justify-end">
      <button type="button" onClick={onClose} className="h-9 rounded-lg border border-clinic-border px-4 text-[10px] font-bold text-clinic-text hover:bg-clinic-soft">Cancelar</button>
      <button type="submit" disabled={isSubmitting} className="h-9 rounded-lg bg-clinic-primary px-4 text-[10px] font-bold text-white disabled:cursor-wait disabled:opacity-70">{isSubmitting ? 'Salvando...' : submitLabel}</button>
    </div>
  );
}

function StatusPill({ active }: { active: boolean }) {
  return (
    <span className={active ? 'rounded-full bg-clinic-primary/10 px-2 py-1 text-[8px] font-bold text-clinic-primary' : 'rounded-full bg-clinic-soft px-2 py-1 text-[8px] font-bold text-clinic-muted'}>
      {active ? 'Ativo' : 'Inativo'}
    </span>
  );
}

function IconButton({ label, icon, onClick, danger }: { label: string; icon: ReactNode; onClick: () => void; danger?: boolean }) {
  return (
    <button type="button" aria-label={label} title={label} onClick={onClick} className={`flex h-8 w-8 items-center justify-center rounded-lg border border-clinic-border ${danger ? 'text-clinic-danger hover:bg-clinic-danger/10' : 'text-clinic-muted hover:bg-clinic-soft hover:text-clinic-text'}`}>
      {icon}
    </button>
  );
}
