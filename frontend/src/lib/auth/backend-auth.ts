import type { AuthUser } from './types';

type BackendLoginResponse = AuthUser & {
  token: string;
};

function backendUrl() {
  return process.env.BACKEND_API_URL
    ?? process.env.NEXT_PUBLIC_API_BASE_URL
    ?? 'http://localhost:8080';
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

export async function readBackendLogin(response: Response): Promise<BackendLoginResponse> {
  return response.json() as Promise<BackendLoginResponse>;
}
