'use client';

import { FormEvent, useEffect, useState } from 'react';
import {
  AlertCircle,
  Mail,
  Phone,
  ShieldCheck,
  Stethoscope,
  UserPlus,
  UsersRound,
  X,
  KeyRound,
  RefreshCw,
  UserCheck,
  UserX,
  Lock,
  ArrowLeft,
  Eye,
  EyeOff,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import Link from 'next/link';
import { PageHeader } from '@/components/demo/PageHeader';
import { FormSelect } from '@/components/ui/form-select';
import { StatusBadge } from '@/components/demo/StatusBadge';
import { DemoCard } from '@/components/demo/DemoCard';
import type { AuthUser } from '@/lib/auth/types';
import type { EquipePerfil, EquipeUsuario } from '@/types/equipe';
import {
  isValidPassword,
  PASSWORD_MAX_BYTES,
  PASSWORD_MIN_LENGTH,
  PASSWORD_RULE_MESSAGE,
} from '@/lib/auth/password-policy';

type AcessosClientProps = {
  user: AuthUser;
};

const PROFILE_LABELS: Record<EquipePerfil, string> = {
  GESTOR: 'Gestor',
  MEDICO: 'Médico',
  RECEPCIONISTA: 'Recepcionista',
};

const PROFILE_ICONS: Record<EquipePerfil, LucideIcon> = {
  GESTOR: ShieldCheck,
  MEDICO: Stethoscope,
  RECEPCIONISTA: UsersRound,
};

export function AcessosClient({ user }: AcessosClientProps) {
  const [usuarios, setUsuarios] = useState<EquipeUsuario[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Modais
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [isResetOpen, setIsResetOpen] = useState(false);
  const [selectedUser, setSelectedUser] = useState<EquipeUsuario | null>(null);
  const [showCreatePassword, setShowCreatePassword] = useState(false);
  const [showResetPassword, setShowResetPassword] = useState(false);

  // Feedbacks
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    void fetchUsuarios();
  }, []);

  async function fetchUsuarios() {
    setLoading(true);
    setError(null);
    try {
      const response = await fetch('/api/admin/usuarios');
      if (!response.ok) {
        if (response.status === 403) {
          throw new Error('Você não tem permissão para gerenciar acessos.');
        }
        throw new Error('Não foi possível carregar os usuários.');
      }
      const data = (await response.json()) as EquipeUsuario[];
      setUsuarios(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Erro ao conectar ao servidor.');
    } finally {
      setLoading(false);
    }
  }

  async function handleCreateUser(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitError(null);
    setIsSubmitting(true);

    const form = new FormData(event.currentTarget);
    const payload = {
      nome: String(form.get('nome') ?? '').trim(),
      email: String(form.get('email') ?? '').trim(),
      perfil: String(form.get('perfil') ?? 'RECEPCIONISTA') as EquipePerfil,
      telefone: String(form.get('telefone') ?? '').trim() || undefined,
      senhaTemporaria: String(form.get('senhaTemporaria') ?? ''),
    };

    if (!isValidPassword(payload.senhaTemporaria)) {
      setSubmitError(PASSWORD_RULE_MESSAGE);
      setIsSubmitting(false);
      return;
    }

    try {
      const response = await fetch('/api/admin/usuarios', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      const body = await safeJson(response);

      if (!response.ok) {
        setSubmitError(getResponseMessage(body) ?? 'Não foi possível criar o usuário.');
        return;
      }

      setUsuarios((prev) => [...prev, body as EquipeUsuario].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')));
      setIsCreateOpen(false);
    } catch {
      setSubmitError('Serviço indisponível no momento.');
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleToggleStatus(targetUser: EquipeUsuario) {
    if (targetUser.id === user.id) {
      alert('Não é permitido desativar a sua própria conta.');
      return;
    }

    const confirmMsg = targetUser.ativo
      ? `Deseja realmente desativar o acesso de ${targetUser.nome}?`
      : `Deseja realmente reativar o acesso de ${targetUser.nome}?`;

    if (!confirm(confirmMsg)) return;

    try {
      const response = await fetch(`/api/admin/usuarios/${targetUser.id}/status`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ativo: !targetUser.ativo }),
      });

      if (!response.ok) {
        const body = await safeJson(response);
        alert(getResponseMessage(body) ?? 'Falha ao alterar o status do usuário.');
        return;
      }

      const updated = (await response.json()) as EquipeUsuario;
      setUsuarios((prev) => prev.map((u) => (u.id === updated.id ? updated : u)));
    } catch {
      alert('Não foi possível alterar o status do usuário.');
    }
  }

  async function handleResetPassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedUser) return;

    setSubmitError(null);
    setIsSubmitting(true);

    const form = new FormData(event.currentTarget);
    const senhaTemporaria = String(form.get('senhaTemporaria') ?? '');

    if (!isValidPassword(senhaTemporaria)) {
      setSubmitError(PASSWORD_RULE_MESSAGE);
      setIsSubmitting(false);
      return;
    }

    try {
      const response = await fetch(`/api/admin/usuarios/${selectedUser.id}/resetar-senha`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ senhaTemporaria }),
      });
      const body = await safeJson(response);

      if (!response.ok) {
        setSubmitError(getResponseMessage(body) ?? 'Falha ao resetar a senha.');
        return;
      }

      const updated = body as EquipeUsuario;
      setUsuarios((prev) => prev.map((u) => (u.id === updated.id ? updated : u)));
      setIsResetOpen(false);
      alert(`Senha de ${selectedUser.nome} redefinida com sucesso!`);
    } catch {
      setSubmitError('Serviço indisponível no momento.');
    } finally {
      setIsSubmitting(false);
    }
  }

  function generateTempPassword(formId: string, inputName: string, reveal: () => void) {
    // Fmna + 4 dígitos + Ok (Garante letras e números)
    const code = Math.floor(1000 + Math.random() * 9000);
    const password = `Fmna${code}Ok`;
    const input = document.querySelector(`#${formId} input[name="${inputName}"]`) as HTMLInputElement;
    if (input) {
      input.value = password;
      reveal();
    }
  }

  return (
    <>
      <div className="mb-2">
        <Link
          href="/configuracoes"
          className="inline-flex items-center gap-1.5 text-[10px] font-bold text-clinic-muted hover:text-clinic-text transition"
        >
          <ArrowLeft className="h-3 w-3" />
          Voltar para Configurações
        </Link>
      </div>

      <PageHeader
        title="Gerenciar Acessos"
        description="Painel de controle e criação de acessos dos usuários da clínica"
        actions={
          <button
            type="button"
            onClick={() => {
              setSubmitError(null);
              setIsCreateOpen(true);
            }}
            className="flex h-8 items-center gap-2 rounded-lg bg-clinic-primary px-3 text-[10px] font-bold text-white transition hover:bg-clinic-primary-strong"
          >
            <UserPlus className="h-3.5 w-3.5" />
            Novo usuário
          </button>
        }
      />

      {error ? (
        <p role="alert" className="mb-3 rounded-lg border border-clinic-danger/30 bg-clinic-danger/10 px-3 py-2 text-[10px] font-semibold text-clinic-danger">
          {error}
        </p>
      ) : null}

      {loading ? (
        <div className="flex items-center justify-center py-16 text-xs text-clinic-muted">
          <RefreshCw className="h-4 w-4 animate-spin mr-2" />
          Carregando usuários da clínica...
        </div>
      ) : usuarios.length === 0 ? (
        <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-clinic-border bg-clinic-surface py-16 text-center">
          <UsersRound className="mb-3 h-10 w-10 text-clinic-muted" />
          <p className="text-[12px] font-bold text-clinic-text">Nenhum usuário cadastrado</p>
        </div>
      ) : (
        <DemoCard
          title="Lista de Acessos"
          description="Exibe todos os integrantes registrados na clínica atual"
          icon={<UsersRound className="h-5 w-5" />}
        >
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse text-xs">
              <thead>
                <tr className="border-b border-clinic-border bg-clinic-canvas/50 text-[10px] uppercase font-bold text-clinic-muted">
                  <th className="p-3">Nome / Perfil</th>
                  <th className="p-3">E-mail</th>
                  <th className="p-3">Telefone</th>
                  <th className="p-3">Status</th>
                  <th className="p-3">Senha</th>
                  <th className="p-3 text-right">Ações</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-clinic-border bg-clinic-surface">
                {usuarios.map((item) => {
                  const Icon = PROFILE_ICONS[item.perfil] ?? UsersRound;
                  return (
                    <tr key={item.id} className="hover:bg-clinic-soft/30 transition-colors">
                      <td className="p-3">
                        <div className="flex items-center gap-2">
                          <span className="flex h-6 w-6 items-center justify-center rounded bg-clinic-soft text-clinic-primary">
                            <Icon className="h-3.5 w-3.5" />
                          </span>
                          <div>
                            <p className="font-bold text-clinic-text">{item.nome}</p>
                            <p className="text-[10px] text-clinic-muted">{PROFILE_LABELS[item.perfil]}</p>
                          </div>
                        </div>
                      </td>
                      <td className="p-3 text-clinic-text">
                        <span className="flex items-center gap-1.5">
                          <Mail className="h-3.5 w-3.5 text-clinic-muted shrink-0" />
                          {item.email}
                        </span>
                      </td>
                      <td className="p-3 text-clinic-muted">
                        {item.telefone ? (
                          <span className="flex items-center gap-1.5">
                            <Phone className="h-3.5 w-3.5 shrink-0" />
                            {item.telefone}
                          </span>
                        ) : (
                          'Não informado'
                        )}
                      </td>
                      <td className="p-3">
                        <div className="flex flex-col gap-1 items-start">
                          <StatusBadge tone={item.ativo ? 'green' : 'slate'}>
                            {item.ativo ? 'Ativo' : 'Inativo'}
                          </StatusBadge>
                        </div>
                      </td>
                      <td className="p-3">
                        {item.mustChangePassword ? (
                          <span className="rounded bg-clinic-orange/10 px-1.5 py-0.5 text-[9px] font-bold text-clinic-orange border border-clinic-orange/20">
                            Troca Pendente
                          </span>
                        ) : (
                          <span className="rounded bg-clinic-primary/10 px-1.5 py-0.5 text-[9px] font-bold text-clinic-primary border border-clinic-primary/20">
                            Ok
                          </span>
                        )}
                      </td>
                      <td className="p-3 text-right">
                        <div className="flex items-center justify-end gap-1.5">
                          <button
                            type="button"
                            onClick={() => {
                              setSelectedUser(item);
                              setSubmitError(null);
                              setIsResetOpen(true);
                            }}
                            title="Redefinir senha temporária"
                            className="flex h-7 w-7 items-center justify-center rounded-lg border border-clinic-border text-clinic-muted transition hover:bg-clinic-soft hover:text-clinic-text"
                          >
                            <KeyRound className="h-3.5 w-3.5" />
                          </button>
                          {item.id !== user.id ? (
                            <button
                              type="button"
                              onClick={() => void handleToggleStatus(item)}
                              title={item.ativo ? 'Desativar acesso' : 'Ativar acesso'}
                              className={`flex h-7 w-7 items-center justify-center rounded-lg border transition ${
                                item.ativo
                                  ? 'border-clinic-danger/20 text-clinic-danger hover:bg-clinic-danger/10'
                                  : 'border-clinic-primary/20 text-clinic-primary hover:bg-clinic-primary/10'
                              }`}
                            >
                              {item.ativo ? <UserX className="h-3.5 w-3.5" /> : <UserCheck className="h-3.5 w-3.5" />}
                            </button>
                          ) : null}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </DemoCard>
      )}

      {/* Modal Criar Usuário */}
      {isCreateOpen ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/35 p-4">
          <section
            role="dialog"
            aria-modal="true"
            aria-labelledby="dialog-create-title"
            className="w-full max-w-lg rounded-2xl border border-clinic-border bg-clinic-surface p-5 shadow-xl"
            id="createUserForm"
          >
            <div className="mb-4 flex items-start justify-between gap-3">
              <div>
                <h2 id="dialog-create-title" className="text-[15px] font-extrabold text-clinic-text">
                  Novo usuário
                </h2>
                <p className="mt-1 text-[10px] text-clinic-muted">
                  A senha informada será temporária e o usuário precisará trocá-la no primeiro acesso.
                </p>
              </div>
              <button
                type="button"
                onClick={() => setIsCreateOpen(false)}
                className="flex h-8 w-8 items-center justify-center rounded-lg text-clinic-muted transition hover:bg-clinic-soft hover:text-clinic-text"
                aria-label="Fechar"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            {submitError ? (
              <p role="alert" className="mb-3 flex items-center gap-2 rounded-lg border border-clinic-danger/30 bg-clinic-danger/10 px-3 py-2 text-[10px] font-semibold text-clinic-danger">
                <AlertCircle className="h-3.5 w-3.5 shrink-0" />
                {submitError}
              </p>
            ) : null}

            <form onSubmit={handleCreateUser} className="space-y-3">
              <label className="block">
                <span className="mb-1.5 block text-[10px] font-bold text-clinic-text">Nome</span>
                <input name="nome" required className="h-10 w-full rounded-lg border border-clinic-border bg-clinic-input px-3 text-sm text-clinic-text outline-none transition focus:border-clinic-primary focus:ring-2 focus:ring-clinic-primary/15" />
              </label>

              <label className="block">
                <span className="mb-1.5 block text-[10px] font-bold text-clinic-text">Email</span>
                <input name="email" type="email" required className="h-10 w-full rounded-lg border border-clinic-border bg-clinic-input px-3 text-sm text-clinic-text outline-none transition focus:border-clinic-primary focus:ring-2 focus:ring-clinic-primary/15" />
              </label>

              <div className="grid gap-3 md:grid-cols-2">
                <label className="block">
                  <span className="mb-1.5 block text-[10px] font-bold text-clinic-text">Perfil</span>
                  <FormSelect
                    name="perfil"
                    required
                    defaultValue="RECEPCIONISTA"
                    options={[
                      { value: 'GESTOR', label: 'Gestor' }, { value: 'MEDICO', label: 'Médico' },
                      { value: 'RECEPCIONISTA', label: 'Recepcionista' },
                    ]}
                  />
                </label>

                <label className="block">
                  <span className="mb-1.5 block text-[10px] font-bold text-clinic-text">Telefone</span>
                  <input name="telefone" inputMode="tel" className="h-10 w-full rounded-lg border border-clinic-border bg-clinic-input px-3 text-sm text-clinic-text outline-none transition focus:border-clinic-primary focus:ring-2 focus:ring-clinic-primary/15" />
                </label>
              </div>

              <div className="block">
                <label htmlFor="senhaTemporariaCriacao" className="mb-1.5 block text-[10px] font-bold text-clinic-text">Senha temporária</label>
                <div className="relative">
                  <input id="senhaTemporariaCriacao" name="senhaTemporaria" type={showCreatePassword ? 'text' : 'password'} minLength={PASSWORD_MIN_LENGTH} maxLength={PASSWORD_MAX_BYTES} required className="h-10 w-full rounded-lg border border-clinic-border bg-clinic-input pl-3 pr-32 text-sm text-clinic-text outline-none transition focus:border-clinic-primary focus:ring-2 focus:ring-clinic-primary/15" />
                  <div className="absolute right-1 top-1 flex items-center gap-1">
                    <button type="button" onClick={() => setShowCreatePassword((current) => !current)} className="flex h-8 w-8 items-center justify-center text-clinic-muted hover:text-clinic-text" aria-label={showCreatePassword ? 'Ocultar senha temporária' : 'Mostrar senha temporária'}>
                      {showCreatePassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                    </button>
                    <button type="button" onClick={() => generateTempPassword('createUserForm', 'senhaTemporaria', () => setShowCreatePassword(true))} className="h-7 rounded bg-clinic-soft px-2 text-[9px] font-bold text-clinic-primary hover:bg-clinic-primary/10 transition">
                      Gerar senha
                    </button>
                  </div>
                </div>
                <span className="mt-1 block text-[9px] text-clinic-muted">
                  {PASSWORD_RULE_MESSAGE}
                </span>
              </div>

              <div className="flex flex-col-reverse gap-2 pt-2 sm:flex-row sm:justify-end">
                <button
                  type="button"
                  onClick={() => setIsCreateOpen(false)}
                  className="h-9 rounded-lg border border-clinic-border px-4 text-[10px] font-bold text-clinic-text transition hover:bg-clinic-soft"
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  disabled={isSubmitting}
                  className="h-9 rounded-lg bg-clinic-primary px-4 text-[10px] font-bold text-white transition hover:bg-clinic-primary-strong disabled:cursor-wait disabled:opacity-70"
                >
                  {isSubmitting ? 'Criando...' : 'Criar usuário'}
                </button>
              </div>
            </form>
          </section>
        </div>
      ) : null}

      {/* Modal Resetar Senha */}
      {isResetOpen && selectedUser ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/35 p-4">
          <section
            role="dialog"
            aria-modal="true"
            aria-labelledby="dialog-reset-title"
            className="w-full max-w-md rounded-2xl border border-clinic-border bg-clinic-surface p-5 shadow-xl"
            id="resetPasswordForm"
          >
            <div className="mb-4 flex items-start justify-between gap-3">
              <div>
                <h2 id="dialog-reset-title" className="text-[15px] font-extrabold text-clinic-text">
                  Redefinir senha
                </h2>
                <p className="mt-1 text-[10px] text-clinic-muted">
                  Redefina a senha temporária para <strong>{selectedUser.nome}</strong>. O usuário precisará trocá-la no próximo login.
                </p>
              </div>
              <button
                type="button"
                onClick={() => setIsResetOpen(false)}
                className="flex h-8 w-8 items-center justify-center rounded-lg text-clinic-muted transition hover:bg-clinic-soft hover:text-clinic-text"
                aria-label="Fechar"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            {submitError ? (
              <p role="alert" className="mb-3 flex items-center gap-2 rounded-lg border border-clinic-danger/30 bg-clinic-danger/10 px-3 py-2 text-[10px] font-semibold text-clinic-danger">
                <AlertCircle className="h-3.5 w-3.5 shrink-0" />
                {submitError}
              </p>
            ) : null}

            <form onSubmit={handleResetPassword} className="space-y-4">
              <div className="block">
                <label htmlFor="senhaTemporariaReset" className="mb-1.5 block text-[10px] font-bold text-clinic-text">Nova Senha Temporária</label>
                <div className="relative">
                  <input id="senhaTemporariaReset" name="senhaTemporaria" type={showResetPassword ? 'text' : 'password'} minLength={PASSWORD_MIN_LENGTH} maxLength={PASSWORD_MAX_BYTES} required className="h-10 w-full rounded-lg border border-clinic-border bg-clinic-input pl-3 pr-32 text-sm text-clinic-text outline-none transition focus:border-clinic-primary focus:ring-2 focus:ring-clinic-primary/15" />
                  <div className="absolute right-1 top-1 flex items-center gap-1">
                    <button type="button" onClick={() => setShowResetPassword((current) => !current)} className="flex h-8 w-8 items-center justify-center text-clinic-muted hover:text-clinic-text" aria-label={showResetPassword ? 'Ocultar nova senha temporária' : 'Mostrar nova senha temporária'}>
                      {showResetPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                    </button>
                    <button type="button" onClick={() => generateTempPassword('resetPasswordForm', 'senhaTemporaria', () => setShowResetPassword(true))} className="h-7 rounded bg-clinic-soft px-2 text-[9px] font-bold text-clinic-primary hover:bg-clinic-primary/10 transition">
                      Gerar senha
                    </button>
                  </div>
                </div>
                <span className="mt-1 block text-[9px] text-clinic-muted">
                  {PASSWORD_RULE_MESSAGE}
                </span>
              </div>

              <div className="flex flex-col-reverse gap-2 pt-2 sm:flex-row sm:justify-end">
                <button
                  type="button"
                  onClick={() => setIsResetOpen(false)}
                  className="h-9 rounded-lg border border-clinic-border px-4 text-[10px] font-bold text-clinic-text transition hover:bg-clinic-soft"
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  disabled={isSubmitting}
                  className="h-9 rounded-lg bg-clinic-primary px-4 text-[10px] font-bold text-white transition hover:bg-clinic-primary-strong disabled:cursor-wait disabled:opacity-70"
                >
                  {isSubmitting ? 'Redefinindo...' : 'Confirmar'}
                </button>
              </div>
            </form>
          </section>
        </div>
      ) : null}
    </>
  );
}

async function safeJson(response: Response): Promise<unknown> {
  try {
    return await response.json();
  } catch {
    return null;
  }
}

function getResponseMessage(body: unknown) {
  if (body && typeof body === 'object' && 'message' in body) {
    return String((body as { message: unknown }).message);
  }
  return undefined;
}
