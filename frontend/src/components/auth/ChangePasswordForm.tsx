'use client';

import { FormEvent, useState } from 'react';
import { Eye, EyeOff, LockKeyhole } from 'lucide-react';
import { useRouter } from 'next/navigation';
import {
  isValidPassword,
  PASSWORD_MAX_BYTES,
  PASSWORD_MIN_LENGTH,
  PASSWORD_RULE_MESSAGE,
} from '@/lib/auth/password-policy';

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
    if (!isValidPassword(payload.novaSenha)) {
      setError(PASSWORD_RULE_MESSAGE);
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
        minLength={PASSWORD_MIN_LENGTH}
      />
      <PasswordField
        label="Confirmar nova senha"
        name="confirmacaoNovaSenha"
        autoComplete="new-password"
        minLength={PASSWORD_MIN_LENGTH}
      />

      <p className="text-[10px] leading-4 text-clinic-muted">
        {PASSWORD_RULE_MESSAGE}
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
  minLength,
}: {
  label: string;
  name: string;
  autoComplete: string;
  minLength?: number;
}) {
  const [isVisible, setIsVisible] = useState(false);

  return (
    <label className="block">
      <span className="mb-1.5 block text-[10px] font-bold text-clinic-text">{label}</span>
      <span className="relative block">
        <LockKeyhole className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-clinic-muted" />
        <input
          name={name}
          type={isVisible ? 'text' : 'password'}
          autoComplete={autoComplete}
          required
          minLength={minLength}
          maxLength={PASSWORD_MAX_BYTES}
          className="h-11 w-full rounded-lg border border-clinic-border bg-clinic-input pl-10 pr-10 text-sm text-clinic-text outline-none transition focus:border-clinic-primary focus:ring-2 focus:ring-clinic-primary/15"
        />
        <button
          type="button"
          className="absolute right-2 top-1/2 flex h-8 w-8 -translate-y-1/2 items-center justify-center text-clinic-muted hover:text-clinic-text"
          onClick={() => setIsVisible((current) => !current)}
          aria-label={isVisible ? `Ocultar ${label.toLowerCase()}` : `Mostrar ${label.toLowerCase()}`}
        >
          {isVisible ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
        </button>
      </span>
    </label>
  );
}
