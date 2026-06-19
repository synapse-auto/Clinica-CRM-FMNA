'use client';

import { FormEvent, useState } from 'react';
import { LockKeyhole, Mail } from 'lucide-react';
import { useRouter } from 'next/navigation';

type LoginResponse = {
  redirectTo?: string;
  message?: string;
};

export function LoginForm() {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setLoading(true);
    const form = new FormData(event.currentTarget);

    try {
      const response = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          email: String(form.get('email') ?? ''),
          senha: String(form.get('senha') ?? ''),
        }),
      });
      const body = await response.json() as LoginResponse;
      if (!response.ok) {
        setError(body.message ?? 'Não foi possível entrar.');
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

  return (
    <form onSubmit={handleSubmit} className="mt-7 space-y-4">
      {error ? (
        <div role="alert" className="rounded-lg border border-clinic-danger/30 bg-clinic-danger/10 px-3 py-2 text-[11px] font-semibold text-clinic-danger">
          {error}
        </div>
      ) : null}
      <label className="block">
        <span className="mb-1.5 block text-[10px] font-bold text-clinic-text">Email</span>
        <span className="relative block">
          <Mail className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-clinic-muted" />
          <input name="email" type="email" autoComplete="username" required className="h-11 w-full rounded-lg border border-clinic-border bg-clinic-input pl-10 pr-3 text-sm text-clinic-text outline-none transition focus:border-clinic-primary focus:ring-2 focus:ring-clinic-primary/15" />
        </span>
      </label>
      <label className="block">
        <span className="mb-1.5 block text-[10px] font-bold text-clinic-text">Senha</span>
        <span className="relative block">
          <LockKeyhole className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-clinic-muted" />
          <input name="senha" type="password" autoComplete="current-password" required className="h-11 w-full rounded-lg border border-clinic-border bg-clinic-input pl-10 pr-3 text-sm text-clinic-text outline-none transition focus:border-clinic-primary focus:ring-2 focus:ring-clinic-primary/15" />
        </span>
      </label>
      <button type="submit" disabled={loading} className="flex h-11 w-full items-center justify-center rounded-lg bg-clinic-primary text-sm font-extrabold text-white transition hover:bg-clinic-primary-strong disabled:cursor-wait disabled:opacity-70">
        {loading ? 'Entrando...' : 'Entrar'}
      </button>
    </form>
  );
}
