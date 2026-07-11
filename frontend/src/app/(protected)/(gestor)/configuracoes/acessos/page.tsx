import { redirect } from 'next/navigation';
import { requireSession } from '@/lib/auth/session';
import { AcessosClient } from '@/components/configuracoes/AcessosClient';

export default async function AcessosPage() {
  const user = await requireSession(['GESTOR']);

  // Bloquear quem não tem permissão explícita
  if (!user.podeGerenciarUsuarios) {
    redirect('/configuracoes');
  }

  return (
    <div className="h-full overflow-auto bg-clinic-canvas p-4 custom-scrollbar">
      <AcessosClient user={user} />
    </div>
  );
}
