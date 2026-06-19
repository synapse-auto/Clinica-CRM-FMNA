import { redirect } from 'next/navigation';
import { defaultRouteForProfile } from '@/lib/auth/permissions';
import { getSession } from '@/lib/auth/session';

export default async function Home() {
  const user = await getSession();
  redirect(user ? defaultRouteForProfile(user.perfil) : '/login');
}
