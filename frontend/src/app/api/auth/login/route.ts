import { cookies } from 'next/headers';
import { NextResponse } from 'next/server';
import { authenticateWithBackend, readBackendLogin } from '@/lib/auth/backend-auth';
import { SESSION_COOKIE_NAME, SESSION_MAX_AGE_SECONDS } from '@/lib/auth/constants';
import { defaultRouteForProfile } from '@/lib/auth/permissions';
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
  } catch {
    return NextResponse.json(
      { message: 'Serviço de autenticação indisponível.' },
      { status: 503 },
    );
  }

  if (!backendResponse.ok) {
    const status = backendResponse.status === 401 ? 401 : 502;
    return NextResponse.json(
      { message: status === 401 ? 'Credenciais inválidas.' : 'Não foi possível autenticar.' },
      { status },
    );
  }

  const { token, ...user } = await readBackendLogin(backendResponse);
  if (!token || !isAuthProfile(user.perfil)) {
    return NextResponse.json({ message: 'Perfil de acesso inválido.' }, { status: 403 });
  }

  const cookieStore = await cookies();
  cookieStore.set(SESSION_COOKIE_NAME, token, {
    httpOnly: true,
    secure: process.env.NODE_ENV === 'production',
    sameSite: 'lax',
    path: '/',
    maxAge: SESSION_MAX_AGE_SECONDS,
  });

  return NextResponse.json({
    user,
    redirectTo: defaultRouteForProfile(user.perfil),
  });
}
