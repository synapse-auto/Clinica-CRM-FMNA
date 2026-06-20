import { cookies } from 'next/headers';
import { NextResponse } from 'next/server';
import {
  changePasswordWithBackend,
  readBackendLogin,
} from '@/lib/auth/backend-auth';
import { SESSION_COOKIE_NAME } from '@/lib/auth/constants';
import { routeAfterAuthentication } from '@/lib/auth/permissions';
import { setSessionCookie } from '@/lib/auth/session-cookie';
import { isAuthProfile } from '@/lib/auth/types';

type ChangePasswordBody = {
  senhaAtual?: unknown;
  novaSenha?: unknown;
  confirmacaoNovaSenha?: unknown;
};

export async function PATCH(request: Request) {
  const cookieStore = await cookies();
  const token = cookieStore.get(SESSION_COOKIE_NAME)?.value;
  if (!token) {
    return NextResponse.json({ message: 'Autenticação necessária.' }, { status: 401 });
  }

  const body = await request.json().catch(() => null) as ChangePasswordBody | null;
  const payload = {
    senhaAtual: stringValue(body?.senhaAtual),
    novaSenha: stringValue(body?.novaSenha),
    confirmacaoNovaSenha: stringValue(body?.confirmacaoNovaSenha),
  };
  if (!payload.senhaAtual || !payload.novaSenha || !payload.confirmacaoNovaSenha) {
    return NextResponse.json({ message: 'Preencha todos os campos.' }, { status: 400 });
  }

  let backendResponse: Response;
  try {
    backendResponse = await changePasswordWithBackend(token, payload);
  } catch {
    return NextResponse.json(
      { message: 'Serviço de autenticação indisponível.' },
      { status: 503 },
    );
  }

  if (!backendResponse.ok) {
    const error = await backendResponse.json().catch(() => null) as { message?: string } | null;
    return NextResponse.json(
      { message: error?.message ?? 'Não foi possível alterar a senha.' },
      { status: backendResponse.status },
    );
  }

  const { token: refreshedToken, ...user } = await readBackendLogin(backendResponse);
  if (!refreshedToken || !isAuthProfile(user.perfil) || user.mustChangePassword) {
    return NextResponse.json({ message: 'Resposta de autenticação inválida.' }, { status: 502 });
  }

  await setSessionCookie(refreshedToken);
  return NextResponse.json({
    user,
    redirectTo: routeAfterAuthentication(user.perfil, user.mustChangePassword),
  });
}

function stringValue(value: unknown) {
  return typeof value === 'string' ? value : '';
}
