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
    <form onSubmit={handleSubmit} className="login-form" aria-busy={loading}>
      {error ? (
        <div id="login-error" role="alert" className="login-error">
          {error}
        </div>
      ) : null}
      <label className="login-field">
        <span className="login-label">Email</span>
        <span className="login-control">
          <Mail className="login-input-icon" aria-hidden="true" />
          <input name="email" type="email" inputMode="email" autoComplete="email" required aria-describedby={error ? 'login-error' : undefined} placeholder="nome@empresa.com" className="login-input" />
        </span>
      </label>
      <label className="login-field">
        <span className="login-label">Senha</span>
        <span className="login-control">
          <LockKeyhole className="login-input-icon" aria-hidden="true" />
          <input name="senha" type="password" autoComplete="current-password" required aria-describedby={error ? 'login-error' : undefined} className="login-input" />
        </span>
      </label>
      <button type="submit" disabled={loading} className="login-submit">
        {loading ? 'Entrando...' : 'Entrar'}
      </button>
    </form>
  );
}
