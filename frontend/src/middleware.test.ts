import { describe, expect, it } from 'vitest';
import { NextRequest } from 'next/server';
import { middleware } from './middleware';
import { SESSION_COOKIE_NAME } from '@/lib/auth/constants';

describe('auth middleware', () => {
  it('should_redirect_unauthenticated_internal_route_to_login', () => {
    const response = middleware(new NextRequest('https://crm.test/agenda?semana=atual'));

    expect(response.status).toBe(307);
    expect(response.headers.get('location')).toBe(
      'https://crm.test/login?next=%2Fagenda%3Fsemana%3Datual',
    );
  });

  it('should_allow_internal_route_when_session_cookie_exists', () => {
    const request = new NextRequest('https://crm.test/dashboard', {
      headers: {
        cookie: `${SESSION_COOKIE_NAME}=jwt-http-only`,
      },
    });

    const response = middleware(request);

    expect(response.headers.get('location')).toBeNull();
  });

  it('should_protect_change_password_route_without_session_cookie', () => {
    const response = middleware(new NextRequest('https://crm.test/alterar-senha'));

    expect(response.status).toBe(307);
    expect(response.headers.get('location')).toBe(
      'https://crm.test/login?next=%2Falterar-senha',
    );
  });
});
