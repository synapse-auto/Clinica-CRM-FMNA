import { CancelamentosClient } from '@/components/cancelamentos/CancelamentosClient';
import { requireSession } from '@/lib/auth/session';

export default async function CancelamentosPage() {
  const user = await requireSession();
  return <CancelamentosClient canDelete={user.perfil === 'GESTOR'} />;
}
