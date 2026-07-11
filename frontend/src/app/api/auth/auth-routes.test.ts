import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SESSION_COOKIE_NAME, SESSION_MAX_AGE_SECONDS } from '@/lib/auth/constants';

const cookieStore = vi.hoisted(() => ({
  get: vi.fn(),
  set: vi.fn(),
  delete: vi.fn(),
}));

vi.mock('next/headers', () => ({
  cookies: async () => cookieStore,
}));

import { PATCH as changePassword } from './change-password/route';
import { POST as login } from './login/route';
import { POST as logout } from './logout/route';
import { GET as me } from './me/route';

describe('auth BFF routes', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.stubEnv('BACKEND_API_URL', 'http://backend.test');
    vi.stubEnv('NODE_ENV', 'production');
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
  });

  it('should_store_jwt_only_in_secure_http_only_cookie_when_login_succeeds', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      token: 'jwt-server-only',
      id: 1,
      nome: 'Gestora',
      email: 'gestora@clinica.local',
      perfil: 'GESTOR',
      clinicaId: 7,
      mustChangePassword: false,
    }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }));
    vi.stubGlobal('fetch', fetchMock);

    const response = await login(new Request('http://localhost/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: 'gestora@clinica.local', senha: 'Senha@123' }),
    }));
    const body = await response.json();

    expect(response.status).toBe(200);
    expect(body).toEqual({
      user: {
        id: 1,
        nome: 'Gestora',
        email: 'gestora@clinica.local',
        perfil: 'GESTOR',
        clinicaId: 7,
        mustChangePassword: false,
      },
      redirectTo: '/dashboard',
    });
    expect(body.token).toBeUndefined();
    expect(fetchMock).toHaveBeenCalledWith('http://backend.test/api/auth/login', expect.objectContaining({
      body: JSON.stringify({ email: 'gestora@clinica.local', senha: 'Senha@123' }),
    }));
    expect(cookieStore.set).toHaveBeenCalledWith(
      SESSION_COOKIE_NAME,
      'jwt-server-only',
      expect.objectContaining({
        httpOnly: true,
        secure: true,
        sameSite: 'lax',
        path: '/',
        maxAge: SESSION_MAX_AGE_SECONDS,
      }),
    );
  });

  it('should_validate_cookie_against_backend_without_exposing_token', async () => {
    cookieStore.get.mockReturnValue({ value: 'jwt-server-only' });
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      id: 2,
      nome: 'Recepção',
      email: 'recepcao@clinica.local',
      perfil: 'RECEPCIONISTA',
      clinicaId: 7,
      mustChangePassword: false,
    }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }));
    vi.stubGlobal('fetch', fetchMock);

    const response = await me();
    const body = await response.json();

    expect(response.status).toBe(200);
    expect(body.token).toBeUndefined();
    expect(fetchMock).toHaveBeenCalledWith(
      'http://backend.test/api/auth/me',
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: 'Bearer jwt-server-only',
        }),
      }),
    );
  });

  it('should_delete_session_cookie_on_logout', async () => {
    const response = await logout();

    expect(response.status).toBe(204);
    expect(cookieStore.delete).toHaveBeenCalledWith(SESSION_COOKIE_NAME);
  });

  it('should_redirect_initial_password_login_to_change_password', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      token: 'jwt-server-only',
      id: 3,
      nome: 'Primeiro Acesso',
      email: 'primeiro@clinica.local',
      perfil: 'GESTOR',
      clinicaId: 7,
      mustChangePassword: true,
    }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    })));

    const response = await login(new Request('http://localhost/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: 'primeiro@clinica.local', senha: 'senha-inicial' }),
    }));
    const body = await response.json();

    expect(body.redirectTo).toBe('/alterar-senha');
    expect(body.token).toBeUndefined();
  });

  it('should_return_clear_service_unavailable_when_backend_url_is_missing', async () => {
    vi.stubEnv('BACKEND_API_URL', '');
    vi.stubEnv('NEXT_PUBLIC_API_BASE_URL', 'http://public-fallback-must-not-be-used.test');
    const fetchMock = vi.fn();
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    vi.stubGlobal('fetch', fetchMock);

    const response = await login(new Request('http://localhost/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: 'gestora@clinica.local', senha: 'segredo-digitado' }),
    }));
    const body = await response.json();

    expect(response.status).toBe(503);
    expect(body.message).toBe('Backend não configurado para autenticação.');
    expect(fetchMock).not.toHaveBeenCalled();
    expect(consoleError).toHaveBeenCalledWith(
      '[auth/login] BACKEND_API_URL não configurada.',
    );
  });

  it('should_report_backend_unavailable_when_upstream_returns_server_error', async () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('Bad Gateway', {
      status: 502,
    })));

    const response = await login(new Request('http://localhost/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: 'gestora@clinica.local', senha: 'segredo-digitado' }),
    }));
    const body = await response.json();

    expect(response.status).toBe(503);
    expect(body.message).toBe('Backend de autenticação indisponível.');
    expect(consoleError).toHaveBeenCalledWith(
      '[auth/login] Backend respondeu com status 502.',
    );
  });

  it('should_replace_http_only_cookie_after_password_change_without_exposing_token', async () => {
    cookieStore.get.mockReturnValue({ value: 'jwt-before-change' });
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      token: 'jwt-after-change',
      id: 3,
      nome: 'Primeiro Acesso',
      email: 'primeiro@clinica.local',
      perfil: 'GESTOR',
      clinicaId: 7,
      mustChangePassword: false,
    }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    })));

    const response = await changePassword(new Request('http://localhost/api/auth/change-password', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        senhaAtual: 'senha-inicial',
        novaSenha: 'Lucas123',
        confirmacaoNovaSenha: 'Lucas123',
      }),
    }));
    const body = await response.json();

    expect(response.status).toBe(200);
    expect(body.token).toBeUndefined();
    expect(body.redirectTo).toBe('/dashboard');
    expect(cookieStore.set).toHaveBeenCalledWith(
      SESSION_COOKIE_NAME,
      'jwt-after-change',
      expect.objectContaining({ httpOnly: true, maxAge: SESSION_MAX_AGE_SECONDS }),
    );
  });
});
