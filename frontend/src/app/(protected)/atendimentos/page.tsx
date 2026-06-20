import { AtendimentosClient } from '@/components/chat/AtendimentosClient';
import { requireSession } from '@/lib/auth/session';
import { getAtendentesAtivos, getAtendimentosIniciais } from '@/services/backend';

export default async function AtendimentosPage() {
  const user = await requireSession(['GESTOR', 'RECEPCIONISTA', 'MEDICO']);
  const initialPage = await getAtendimentosIniciais();
  const atendentes = user.perfil === 'MEDICO' ? [] : await getAtendentesAtivos();

  return (
    <AtendimentosClient
      initialConversations={initialPage.content}
      atendentes={atendentes}
      user={user}
    />
  );
}
