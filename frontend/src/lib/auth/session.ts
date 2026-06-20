import 'server-only';

import { cookies } from 'next/headers';
import { redirect } from 'next/navigation';
import { validateBackendSession } from './backend-auth';
import { SESSION_COOKIE_NAME } from './constants';
import { defaultRouteForProfile } from './permissions';
import { isAuthProfile, type AuthProfile, type AuthUser } from './types';

export async function getSession(): Promise<AuthUser | null> {
  const cookieStore = await cookies();
  const token = cookieStore.get(SESSION_COOKIE_NAME)?.value;
  if (!token) return null;

  try {
    const response = await validateBackendSession(token);
    if (!response.ok) return null;
    const user = await response.json() as AuthUser;
    return isAuthProfile(user.perfil) ? user : null;
  } catch {
    return null;
  }
}

export async function requireSession(
  allowedProfiles?: readonly AuthProfile[],
): Promise<AuthUser> {
  const user = await getSession();
  if (!user) redirect('/login');
  if (user.mustChangePassword) redirect('/alterar-senha');
  if (allowedProfiles && !allowedProfiles.includes(user.perfil)) {
    redirect(defaultRouteForProfile(user.perfil));
  }
  return user;
}
