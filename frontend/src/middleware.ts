import { NextRequest, NextResponse } from 'next/server';
import { SESSION_COOKIE_NAME } from '@/lib/auth/constants';

export function middleware(request: NextRequest) {
  if (request.cookies.has(SESSION_COOKIE_NAME)) {
    return NextResponse.next();
  }

  const loginUrl = request.nextUrl.clone();
  loginUrl.pathname = '/login';
  loginUrl.search = '';
  loginUrl.searchParams.set(
    'next',
    `${request.nextUrl.pathname}${request.nextUrl.search}`,
  );
  return NextResponse.redirect(loginUrl);
}

export const config = {
  matcher: [
    '/dashboard/:path*',
    '/agenda/:path*',
    '/pacientes/:path*',
    '/equipe/:path*',
    '/automacao-ia/:path*',
    '/tags/:path*',
    '/msgs-rapidas/:path*',
    '/horarios/:path*',
    '/configuracoes/:path*',
    '/atendimentos/:path*',
  ],
};
