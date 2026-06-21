import { NextResponse } from 'next/server';
import {
  authenticateWithBackend,
  BackendConfigurationError,
  readBackendLogin,
} from '@/lib/auth/backend-auth';
import { routeAfterAuthentication } from '@/lib/auth/permissions';
import { setSessionCookie } from '@/lib/auth/session-cookie';
import { isAuthProfile } from '@/lib/auth/types';

type LoginBody = {
  email?: unknown;
  senha?: unknown;
};

export async function POST(request: Request) {
  const body = await request.json().catch(() => null) as LoginBody | null;
  const email = typeof body?.email === 'string' ? body.email.trim() : '';
  const senha = typeof body?.senha === 'string' ? body.senha : '';

  if (!email || !senha) {
    return NextResponse.json({ message: 'Informe email e senha.' }, { status: 400 });
  }

  let backendResponse: Response;
  try {
    backendResponse = await authenticateWithBackend(email, senha);
  } catch (error) {
    if (error instanceof BackendConfigurationError) {
      console.error('[auth/login] BACKEND_API_URL não configurada.');
      return NextResponse.json(
        { message: 'Backend não configurado para autenticação.' },
        { status: 503 },
      );
    }
    console.error('[auth/login] Falha ao conectar ao backend.');
    return NextResponse.json(
      { message: 'Backend de autenticação indisponível.' },
      { status: 503 },
    );
  }

  if (!backendResponse.ok) {
    if (backendResponse.status >= 500) {
      console.error(`[auth/login] Backend respondeu com status ${backendResponse.status}.`);
      return NextResponse.json(
        { message: 'Backend de autenticação indisponível.' },
        { status: 503 },
      );
    }

    const status = backendResponse.status === 401 ? 401 : backendResponse.status;
    return NextResponse.json(
      {
        message: status === 401
          ? 'Credenciais inválidas.'
          : 'Backend recusou a solicitação de autenticação.',
      },
      { status },
    );
  }

  const { token, ...user } = await readBackendLogin(backendResponse);
  if (!token || !isAuthProfile(user.perfil)) {
    return NextResponse.json({ message: 'Perfil de acesso inválido.' }, { status: 403 });
  }

  await setSessionCookie(token);

  return NextResponse.json({
    user,
    redirectTo: routeAfterAuthentication(user.perfil, user.mustChangePassword),
  });
}
