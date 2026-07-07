import { MinhaContaClient } from '@/components/conta/MinhaContaClient';
import { requireSession } from '@/lib/auth/session';
import { getClinicaAtual } from '@/services/backend';

export default async function MinhaContaPage() {
  const [user, clinic] = await Promise.all([
    requireSession(),
    getClinicaAtual(),
  ]);

  return <MinhaContaClient user={user} clinic={clinic} />;
}
