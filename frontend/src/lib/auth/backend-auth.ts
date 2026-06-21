import type { AuthUser } from './types';

type BackendLoginResponse = AuthUser & {
  token: string;
};

function backendUrl() {
  const url = process.env.BACKEND_API_URL?.trim();
  if (!url) {
    throw new BackendConfigurationError();
  }
  return url.replace(/\/+$/, '');
}

export class BackendConfigurationError extends Error {
  constructor() {
    super('BACKEND_API_URL não configurada.');
    this.name = 'BackendConfigurationError';
  }
}

export async function authenticateWithBackend(
  email: string,
  senha: string,
): Promise<Response> {
  return fetch(`${backendUrl()}/api/auth/login`, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ email, senha }),
    cache: 'no-store',
  });
}

export async function validateBackendSession(token: string): Promise<Response> {
  return fetch(`${backendUrl()}/api/auth/me`, {
    headers: {
      Accept: 'application/json',
      Authorization: `Bearer ${token}`,
    },
    cache: 'no-store',
  });
}

export async function changePasswordWithBackend(
  token: string,
  payload: {
    senhaAtual: string;
    novaSenha: string;
    confirmacaoNovaSenha: string;
  },
): Promise<Response> {
  return fetch(`${backendUrl()}/api/auth/change-password`, {
    method: 'PATCH',
    headers: {
      Accept: 'application/json',
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
    cache: 'no-store',
  });
}

export async function readBackendLogin(response: Response): Promise<BackendLoginResponse> {
  return response.json() as Promise<BackendLoginResponse>;
}
