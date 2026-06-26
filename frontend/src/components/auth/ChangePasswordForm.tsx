'use client';

import { FormEvent, useState } from 'react';
import { LockKeyhole } from 'lucide-react';
import { useRouter } from 'next/navigation';

type ChangePasswordResponse = {
  redirectTo?: string;
  message?: string;
};

export function ChangePasswordForm() {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    const form = new FormData(event.currentTarget);
    const payload = {
      senhaAtual: String(form.get('senhaAtual') ?? ''),
      novaSenha: String(form.get('novaSenha') ?? ''),
      confirmacaoNovaSenha: String(form.get('confirmacaoNovaSenha') ?? ''),
    };

    if (payload.novaSenha !== payload.confirmacaoNovaSenha) {
      setError('As senhas não coincidem.');
      return;
    }
    if (!isStrongPassword(payload.novaSenha)) {
      setError('A senha deve ter pelo menos 8 caracteres.');
      return;
    }

    setLoading(true);
    try {
      const response = await fetch('/api/auth/change-password', {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      const body = await response.json() as ChangePasswordResponse;
      if (!response.ok) {
        setError(body.message ?? 'Não foi possível alterar a senha.');
        return;
      }
      router.replace(body.redirectTo ?? '/dashboard');
      router.refresh();
    } catch {
      setError('Serviço de autenticação indisponível.');
    } finally {
      setLoading(false);
    }
  }

  async function logout() {
    await fetch('/api/auth/logout', { method: 'POST' });
    router.replace('/login');
    router.refresh();
  }

  return (
    <form onSubmit={handleSubmit} className="mt-7 space-y-4">
      {error ? (
        <div role="alert" className="rounded-lg border border-clinic-danger/30 bg-clinic-danger/10 px-3 py-2 text-[11px] font-semibold text-clinic-danger">
          {error}
        </div>
      ) : null}

      <PasswordField
        label="Senha atual"
        name="senhaAtual"
        autoComplete="current-password"
      />
      <PasswordField
        label="Nova senha"
        name="novaSenha"
        autoComplete="new-password"
      />
      <PasswordField
        label="Confirmar nova senha"
        name="confirmacaoNovaSenha"
        autoComplete="new-password"
      />

      <p className="text-[10px] leading-4 text-clinic-muted">
        A senha deve ter pelo menos 8 caracteres.
      </p>

      <button
        type="submit"
        disabled={loading}
        className="flex h-11 w-full items-center justify-center rounded-lg bg-clinic-primary text-sm font-extrabold text-white transition hover:bg-clinic-primary-strong disabled:cursor-wait disabled:opacity-70"
      >
        {loading ? 'Alterando...' : 'Alterar senha'}
      </button>
      <button
        type="button"
        onClick={logout}
        className="flex h-10 w-full items-center justify-center rounded-lg border border-clinic-border bg-clinic-surface text-xs font-bold text-clinic-text transition hover:bg-clinic-hover"
      >
        Sair
      </button>
    </form>
  );
}

function PasswordField({
  label,
  name,
  autoComplete,
}: {
  label: string;
  name: string;
  autoComplete: string;
}) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-[10px] font-bold text-clinic-text">{label}</span>
      <span className="relative block">
        <LockKeyhole className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-clinic-muted" />
        <input
          name={name}
          type="password"
          autoComplete={autoComplete}
          required
          minLength={8}
          maxLength={72}
          className="h-11 w-full rounded-lg border border-clinic-border bg-clinic-input pl-10 pr-3 text-sm text-clinic-text outline-none transition focus:border-clinic-primary focus:ring-2 focus:ring-clinic-primary/15"
        />
      </span>
    </label>
  );
}

function isStrongPassword(password: string) {
  return password.length >= 8;
}
