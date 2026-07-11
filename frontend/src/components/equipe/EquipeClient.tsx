'use client';

import { FormEvent, useMemo, useState } from 'react';
import {
  AlertCircle,
  Mail,
  Phone,
  ShieldCheck,
  Stethoscope,
  UserPlus,
  UsersRound,
  X,
  Eye,
  EyeOff,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { PageHeader } from '@/components/demo/PageHeader';
import type {
  EquipeGrupo,
  EquipePerfil,
  EquipeResponse,
  EquipeUsuario,
  EquipeUsuarioCreatePayload,
} from '@/types/equipe';
import {
  isValidPassword,
  PASSWORD_MAX_BYTES,
  PASSWORD_MIN_LENGTH,
  PASSWORD_RULE_MESSAGE,
} from '@/lib/auth/password-policy';

type EquipeClientProps = {
  initialData: EquipeResponse;
  initialError: string | null;
};

const PROFILE_LABELS: Record<EquipePerfil, string> = {
  GESTOR: 'Gestor',
  MEDICO: 'Médico',
  RECEPCIONISTA: 'Recepcionista',
};

const GROUP_ICONS: Record<EquipePerfil, LucideIcon> = {
  GESTOR: ShieldCheck,
  MEDICO: Stethoscope,
  RECEPCIONISTA: UsersRound,
};

const AVATAR_TONES = [
  'bg-clinic-primary text-white',
  'bg-clinic-blue text-white',
  'bg-clinic-indigo text-white',
  'bg-clinic-cyan text-white',
  'bg-clinic-orange text-white',
  'bg-clinic-pink text-white',
];

export function EquipeClient({ initialData, initialError }: EquipeClientProps) {
  const [grupos, setGrupos] = useState<EquipeGrupo[]>(initialData.grupos);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const totalUsuarios = useMemo(
    () => grupos.reduce((total, grupo) => total + grupo.usuarios.length, 0),
    [grupos],
  );

  async function handleCreateUser(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitError(null);
    setIsSubmitting(true);

    const form = new FormData(event.currentTarget);
    const payload: EquipeUsuarioCreatePayload = {
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
      const response = await fetch('/api/equipe/usuarios', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      const body = await safeJson(response);

      if (!response.ok) {
        setSubmitError(getResponseMessage(body) ?? 'Não foi possível criar o usuário.');
        return;
      }

      addUser(body as EquipeUsuario);
      setIsModalOpen(false);
    } catch {
      setSubmitError('Serviço de equipe indisponível. Tente novamente em instantes.');
    } finally {
      setIsSubmitting(false);
    }
  }

  function addUser(usuario: EquipeUsuario) {
    setGrupos((current) => current.map((grupo) => (
      grupo.perfil === usuario.perfil
        ? {
            ...grupo,
            usuarios: [...grupo.usuarios, usuario].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')),
          }
        : grupo
    )));
  }

  return (
    <>
      <PageHeader
        title="Equipe"
        description={totalUsuarios === 1 ? '1 usuário cadastrado' : `${totalUsuarios} usuários cadastrados`}
        actions={(
          <button
            type="button"
            onClick={() => {
              setSubmitError(null);
              setIsModalOpen(true);
            }}
            className="flex h-8 items-center gap-2 rounded-lg bg-clinic-primary px-3 text-[10px] font-bold text-white transition hover:bg-clinic-primary-strong"
          >
            <UserPlus className="h-3.5 w-3.5" />
            Novo usuário
          </button>
        )}
      />

      {initialError ? (
        <p role="alert" className="mb-3 rounded-lg border border-clinic-danger/30 bg-clinic-danger/10 px-3 py-2 text-[10px] font-semibold text-clinic-danger">
          {initialError}
        </p>
      ) : null}

      {totalUsuarios === 0 && !initialError ? (
        <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-clinic-border bg-clinic-surface py-16 text-center">
          <UsersRound className="mb-3 h-10 w-10 text-clinic-muted" />
          <p className="text-[12px] font-bold text-clinic-text">Nenhum usuário cadastrado</p>
          <p className="mt-1 max-w-sm text-[10px] text-clinic-muted">
            Adicione gestores, médicos ou recepcionistas reais para liberar o acesso operacional da equipe.
          </p>
        </div>
      ) : (
        <div className="space-y-4">
          {grupos.map((grupo) => (
            <TeamGroup key={grupo.perfil} grupo={grupo} />
          ))}
        </div>
      )}

      {isModalOpen ? (
        <CreateUserDialog
          isSubmitting={isSubmitting}
          error={submitError}
          onClose={() => setIsModalOpen(false)}
          onSubmit={handleCreateUser}
        />
      ) : null}
    </>
  );
}

function TeamGroup({ grupo }: { grupo: EquipeGrupo }) {
  const Icon = GROUP_ICONS[grupo.perfil] ?? UsersRound;

  return (
    <section>
      <div className="mb-2 flex items-center gap-2">
        <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-clinic-soft text-clinic-primary">
          <Icon className="h-3.5 w-3.5" />
        </span>
        <h2 className="text-[11px] font-extrabold text-clinic-text">{grupo.titulo}</h2>
        <span className="rounded-full bg-clinic-primary/10 px-2 py-0.5 text-[9px] font-bold text-clinic-primary">
          {grupo.usuarios.length}
        </span>
      </div>

      <div className="overflow-hidden rounded-xl border border-clinic-border bg-clinic-surface shadow-[0_1px_2px_rgba(4,32,36,0.04)]">
        {grupo.usuarios.length > 0 ? (
          grupo.usuarios.map((usuario, index) => (
            <TeamMember
              key={usuario.id}
              usuario={usuario}
              avatarTone={AVATAR_TONES[index % AVATAR_TONES.length]}
            />
          ))
        ) : (
          <p className="px-4 py-5 text-[10px] font-semibold text-clinic-muted">
            Nenhum usuário neste perfil.
          </p>
        )}
      </div>
    </section>
  );
}

function TeamMember({
  usuario,
  avatarTone,
}: {
  usuario: EquipeUsuario;
  avatarTone: string;
}) {
  return (
    <article className="flex min-h-[76px] flex-col gap-3 border-b border-clinic-border/80 px-4 py-3 last:border-b-0 md:flex-row md:items-center md:justify-between">
      <div className="flex min-w-0 items-center gap-3">
        <span className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl text-[11px] font-extrabold shadow-sm ${avatarTone}`}>
          {iniciais(usuario.nome)}
        </span>
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="truncate text-[11px] font-extrabold text-clinic-text">{usuario.nome}</h3>
            <span className="rounded-full bg-clinic-primary/10 px-2 py-0.5 text-[8px] font-bold text-clinic-primary">
              {PROFILE_LABELS[usuario.perfil]}
            </span>
            {usuario.mustChangePassword ? (
              <span className="rounded-full bg-clinic-orange/10 px-2 py-0.5 text-[8px] font-bold text-clinic-orange">
                Troca de senha pendente
              </span>
            ) : null}
          </div>
          <p className="mt-0.5 flex items-center gap-1 truncate text-[9px] text-clinic-muted">
            <Mail className="h-3 w-3 shrink-0" />
            {usuario.email}
          </p>
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-x-4 gap-y-1 md:justify-end">
        <p className="flex items-center justify-end gap-1 text-right text-[9px] text-clinic-muted">
          <Phone className="h-3 w-3" />
          {usuario.telefone ?? 'Telefone não informado'}
        </p>
      </div>
    </article>
  );
}

function CreateUserDialog({
  isSubmitting,
  error,
  onClose,
  onSubmit,
}: {
  isSubmitting: boolean;
  error: string | null;
  onClose: () => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}) {
  const [showPassword, setShowPassword] = useState(false);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/35 p-4">
      <section
        role="dialog"
        aria-modal="true"
        aria-labelledby="novo-usuario-title"
        className="w-full max-w-lg rounded-2xl border border-clinic-border bg-clinic-surface p-5 shadow-xl"
      >
        <div className="mb-4 flex items-start justify-between gap-3">
          <div>
            <h2 id="novo-usuario-title" className="text-[15px] font-extrabold text-clinic-text">
              Novo usuário
            </h2>
            <p className="mt-1 text-[10px] text-clinic-muted">
              A senha informada será temporária e o usuário precisará trocá-la no primeiro acesso.
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="flex h-8 w-8 items-center justify-center rounded-lg text-clinic-muted transition hover:bg-clinic-soft hover:text-clinic-text"
            aria-label="Fechar"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        {error ? (
          <p role="alert" className="mb-3 flex items-center gap-2 rounded-lg border border-clinic-danger/30 bg-clinic-danger/10 px-3 py-2 text-[10px] font-semibold text-clinic-danger">
            <AlertCircle className="h-3.5 w-3.5" />
            {error}
          </p>
        ) : null}

        <form onSubmit={onSubmit} className="space-y-3">
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
              <select name="perfil" required defaultValue="RECEPCIONISTA" className="h-10 w-full rounded-lg border border-clinic-border bg-clinic-input px-3 text-sm text-clinic-text outline-none transition focus:border-clinic-primary focus:ring-2 focus:ring-clinic-primary/15">
                <option value="GESTOR">Gestor</option>
                <option value="MEDICO">Médico</option>
                <option value="RECEPCIONISTA">Recepcionista</option>
              </select>
            </label>

            <label className="block">
              <span className="mb-1.5 block text-[10px] font-bold text-clinic-text">Telefone</span>
              <input name="telefone" inputMode="tel" className="h-10 w-full rounded-lg border border-clinic-border bg-clinic-input px-3 text-sm text-clinic-text outline-none transition focus:border-clinic-primary focus:ring-2 focus:ring-clinic-primary/15" />
            </label>
          </div>

          <label className="block">
            <span className="mb-1.5 block text-[10px] font-bold text-clinic-text">Senha temporária</span>
            <span className="relative mt-1 block">
              <input name="senhaTemporaria" aria-label="Senha temporária" type={showPassword ? 'text' : 'password'} minLength={PASSWORD_MIN_LENGTH} maxLength={PASSWORD_MAX_BYTES} required className="h-10 w-full rounded-lg border border-clinic-border bg-clinic-input px-3 pr-10 text-sm text-clinic-text outline-none transition focus:border-clinic-primary focus:ring-2 focus:ring-clinic-primary/15" />
              <button type="button" onClick={() => setShowPassword((current) => !current)} className="absolute right-1 top-1 flex h-8 w-8 items-center justify-center text-clinic-muted hover:text-clinic-text" aria-label={showPassword ? 'Ocultar senha temporária' : 'Mostrar senha temporária'}>
                {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
              </button>
            </span>
            <span className="mt-1 block text-[9px] text-clinic-muted">
              {PASSWORD_RULE_MESSAGE}
            </span>
          </label>

          <div className="flex flex-col-reverse gap-2 pt-2 sm:flex-row sm:justify-end">
            <button
              type="button"
              onClick={onClose}
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
  );
}

async function safeJson(response: Response): Promise<{ message?: string } | EquipeUsuario | null> {
  try {
    return await response.json();
  } catch {
    return null;
  }
}

function getResponseMessage(body: { message?: string } | EquipeUsuario | null) {
  if (body && 'message' in body) {
    return body.message;
  }
  return undefined;
}

function iniciais(nome: string) {
  return nome
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('');
}
