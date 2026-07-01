'use client';

import { type FormEvent, type ReactNode, useState } from 'react';
import { AlertCircle, Copy, Edit3, Plus, ToggleLeft, ToggleRight, Trash2, X, Zap } from 'lucide-react';
import { EmptyState } from '@/components/demo/EmptyState';
import { PageHeader } from '@/components/demo/PageHeader';
import type {
  CategoriaMensagemRapida,
  MensagemRapida,
  MensagemRapidaPayload,
} from '@/types/operacional';
import { getResponseMessage, isApiErrorBody, safeJson } from './client-helpers';

type MensagensRapidasClientProps = {
  initialMessages: MensagemRapida[];
  categories: CategoriaMensagemRapida[];
  initialError: string | null;
  canManage: boolean;
};

export function MensagensRapidasClient({
  initialMessages,
  categories,
  initialError,
  canManage,
}: MensagensRapidasClientProps) {
  const [messages, setMessages] = useState(initialMessages);
  const [editing, setEditing] = useState<MensagemRapida | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  function openCreate() {
    setEditing(null);
    setSubmitError(null);
    setIsModalOpen(true);
  }

  function openEdit(message: MensagemRapida) {
    setEditing(message);
    setSubmitError(null);
    setIsModalOpen(true);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitError(null);
    setIsSubmitting(true);

    const form = new FormData(event.currentTarget);
    const categoriaIdValue = String(form.get('categoriaId') ?? '');
    const payload: MensagemRapidaPayload = {
      categoriaId: categoriaIdValue ? Number(categoriaIdValue) : null,
      titulo: String(form.get('titulo') ?? '').trim(),
      atalho: String(form.get('atalho') ?? '').trim(),
      conteudo: String(form.get('conteudo') ?? '').trim(),
      ativo: form.get('ativo') === 'on',
    };

    try {
      const response = await fetch(editing ? `/api/mensagens-rapidas/${editing.id}` : '/api/mensagens-rapidas', {
        method: editing ? 'PUT' : 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      const body = await safeJson<MensagemRapida>(response);

      if (!response.ok || !body || isApiErrorBody(body)) {
        setSubmitError(getResponseMessage(body, 'Não foi possível salvar a mensagem rápida.'));
        return;
      }

      upsertMessage(body);
      setIsModalOpen(false);
    } catch {
      setSubmitError('Serviço de mensagens rápidas indisponível. Tente novamente em instantes.');
    } finally {
      setIsSubmitting(false);
    }
  }

  async function toggleStatus(message: MensagemRapida) {
    const response = await fetch(`/api/mensagens-rapidas/${message.id}/status`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ativo: !message.ativo }),
    });
    const body = await safeJson<MensagemRapida>(response);
    if (response.ok && body && !isApiErrorBody(body)) {
      upsertMessage(body);
    }
  }

  async function deleteMessage(message: MensagemRapida) {
    if (!window.confirm(`Excluir a mensagem "${message.titulo}"?`)) return;
    const response = await fetch(`/api/mensagens-rapidas/${message.id}`, { method: 'DELETE' });
    if (response.ok) {
      setMessages((current) => current.filter((item) => item.id !== message.id));
    }
  }

  function upsertMessage(message: MensagemRapida) {
    setMessages((current) => {
      const exists = current.some((item) => item.id === message.id);
      const next = exists
        ? current.map((item) => (item.id === message.id ? message : item))
        : [...current, message];
      return next.sort((a, b) => a.titulo.localeCompare(b.titulo, 'pt-BR'));
    });
  }

  return (
    <>
      <PageHeader
        title="Mensagens Rápidas"
        description={messages.length === 1 ? '1 mensagem cadastrada' : `${messages.length} mensagens cadastradas`}
        actions={canManage ? (
          <button type="button" onClick={openCreate} className="flex h-8 items-center gap-2 rounded-lg bg-clinic-primary px-3 text-[10px] font-bold text-white">
            <Plus className="h-3.5 w-3.5" />
            Nova mensagem
          </button>
        ) : null}
      />

      {initialError ? <ErrorMessage message={initialError} /> : null}

      {messages.length === 0 && !initialError ? (
        <EmptyState
          icon={Zap}
          title="Nenhuma mensagem rápida cadastrada"
          description="Crie respostas reais para agilizar atendimentos da UltraMedical."
        />
      ) : (
        <div className="grid grid-cols-1 gap-3 lg:grid-cols-2 xl:grid-cols-3">
          {messages.map((message) => (
            <article key={message.id} className="flex min-h-[190px] flex-col rounded-xl border border-clinic-border bg-clinic-surface p-4 shadow-[0_1px_2px_rgba(4,32,36,0.04)]">
              <div className="mb-3 flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-clinic-primary/10 text-clinic-primary">
                      <Copy className="h-3.5 w-3.5" />
                    </span>
                    <div className="min-w-0">
                      <h2 className="truncate text-[12px] font-extrabold text-clinic-text">{message.titulo}</h2>
                      <p className="text-[9px] font-bold text-clinic-primary">{message.atalho}</p>
                    </div>
                  </div>
                </div>
                <StatusPill active={message.ativo} />
              </div>
              <p className="line-clamp-4 flex-1 text-[10px] leading-5 text-clinic-muted">{message.conteudo}</p>
              <p className="mt-3 text-[9px] font-bold text-clinic-muted">{message.categoriaRotulo ?? 'Sem categoria'}</p>

              {canManage ? (
                <div className="mt-3 flex flex-wrap gap-2">
                  <IconButton label="Editar mensagem" onClick={() => openEdit(message)} icon={<Edit3 className="h-3.5 w-3.5" />} />
                  <IconButton
                    label={message.ativo ? 'Desativar mensagem' : 'Ativar mensagem'}
                    onClick={() => void toggleStatus(message)}
                    icon={message.ativo ? <ToggleRight className="h-3.5 w-3.5" /> : <ToggleLeft className="h-3.5 w-3.5" />}
                  />
                  <IconButton label="Excluir mensagem" onClick={() => void deleteMessage(message)} icon={<Trash2 className="h-3.5 w-3.5" />} danger />
                </div>
              ) : null}
            </article>
          ))}
        </div>
      )}

      {isModalOpen ? (
        <MensagemDialog
          message={editing}
          categories={categories}
          error={submitError}
          isSubmitting={isSubmitting}
          onClose={() => setIsModalOpen(false)}
          onSubmit={handleSubmit}
        />
      ) : null}
    </>
  );
}

function MensagemDialog({
  message,
  categories,
  error,
  isSubmitting,
  onClose,
  onSubmit,
}: {
  message: MensagemRapida | null;
  categories: CategoriaMensagemRapida[];
  error: string | null;
  isSubmitting: boolean;
  onClose: () => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/35 p-4">
      <section role="dialog" aria-modal="true" aria-labelledby="mensagem-dialog-title" className="w-full max-w-xl rounded-2xl border border-clinic-border bg-clinic-surface p-5 shadow-xl">
        <DialogHeader id="mensagem-dialog-title" title={message ? 'Editar mensagem rápida' : 'Nova mensagem rápida'} onClose={onClose} />
        {error ? <ErrorMessage message={error} /> : null}
        <form onSubmit={onSubmit} className="space-y-3">
          <Field label="Título" name="titulo" defaultValue={message?.titulo ?? ''} required />
          <Field label="Atalho" name="atalho" defaultValue={message?.atalho ?? ''} required />
          <label className="block">
            <span className="mb-1.5 block text-[10px] font-bold text-clinic-text">Categoria</span>
            <select name="categoriaId" defaultValue={message?.categoriaId ?? ''} className="h-10 w-full rounded-lg border border-clinic-border bg-clinic-input px-3 text-sm text-clinic-text outline-none focus:border-clinic-primary">
              <option value="">Sem categoria</option>
              {categories.map((category) => (
                <option key={category.id} value={category.id}>{category.rotulo}</option>
              ))}
            </select>
          </label>
          <label className="block">
            <span className="mb-1.5 block text-[10px] font-bold text-clinic-text">Conteúdo</span>
            <textarea name="conteudo" required defaultValue={message?.conteudo ?? ''} className="h-28 w-full resize-none rounded-lg border border-clinic-border bg-clinic-input p-3 text-sm text-clinic-text outline-none focus:border-clinic-primary" />
          </label>
          <ActiveCheckbox defaultChecked={message?.ativo ?? true} />
          <DialogActions isSubmitting={isSubmitting} submitLabel="Salvar mensagem" onClose={onClose} />
        </form>
      </section>
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

function Field({ label, name, defaultValue, required }: { label: string; name: string; defaultValue: string; required?: boolean }) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-[10px] font-bold text-clinic-text">{label}</span>
      <input name={name} defaultValue={defaultValue} required={required} className="h-10 w-full rounded-lg border border-clinic-border bg-clinic-input px-3 text-sm text-clinic-text outline-none focus:border-clinic-primary" />
    </label>
  );
}

function ActiveCheckbox({ defaultChecked }: { defaultChecked: boolean }) {
  return (
    <label className="flex items-center gap-2 text-[10px] font-bold text-clinic-text">
      <input name="ativo" type="checkbox" defaultChecked={defaultChecked} className="h-4 w-4 accent-clinic-primary" />
      Ativo
    </label>
  );
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
