import { cookies } from 'next/headers';
import { NextResponse } from 'next/server';
import { validateBackendSession } from '@/lib/auth/backend-auth';
import { SESSION_COOKIE_NAME } from '@/lib/auth/constants';
import { isAuthProfile } from '@/lib/auth/types';

export async function GET() {
  const cookieStore = await cookies();
  const token = cookieStore.get(SESSION_COOKIE_NAME)?.value;

  if (!token) {
    return NextResponse.json({ message: 'Autenticação necessária.' }, { status: 401 });
  }

  let backendResponse: Response;
  try {
    backendResponse = await validateBackendSession(token);
  } catch {
    return NextResponse.json(
      { message: 'Serviço de autenticação indisponível.' },
      { status: 503 },
    );
  }

  if (!backendResponse.ok) {
    if (backendResponse.status === 401 || backendResponse.status === 403) {
      cookieStore.delete(SESSION_COOKIE_NAME);
    }
    return NextResponse.json(
      { message: backendResponse.status === 403 ? 'Acesso negado.' : 'Sessão inválida.' },
      { status: backendResponse.status === 403 ? 403 : 401 },
    );
  }

  const user = await backendResponse.json();
  if (!isAuthProfile(user?.perfil)) {
    cookieStore.delete(SESSION_COOKIE_NAME);
    return NextResponse.json({ message: 'Sessão inválida.' }, { status: 401 });
  }

  return NextResponse.json(user);
}
