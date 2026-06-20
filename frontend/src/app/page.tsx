import { redirect } from 'next/navigation';
import { routeAfterAuthentication } from '@/lib/auth/permissions';
import { getSession } from '@/lib/auth/session';

export default async function Home() {
  const user = await getSession();
  redirect(user ? routeAfterAuthentication(user.perfil, user.mustChangePassword) : '/login');
}
