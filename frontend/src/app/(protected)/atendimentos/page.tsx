import { AtendimentosClient } from '@/components/chat/AtendimentosClient';
import { requireSession } from '@/lib/auth/session';
import { getAtendentesAtivos, getAtendimentosIniciais } from '@/services/backend';
import type { AtendimentoView } from '@/types/atendimento';

export default async function AtendimentosPage({
  searchParams,
}: {
  searchParams: Promise<{ atendimentoId?: string | string[]; visao?: string | string[] }>;
}) {
  const user = await requireSession(['GESTOR', 'RECEPCIONISTA', 'MEDICO']);
  const params = await searchParams;
  const rawAtendimentoId = Array.isArray(params.atendimentoId)
    ? params.atendimentoId[0]
    : params.atendimentoId;
  const rawVisao = Array.isArray(params.visao) ? params.visao[0] : params.visao;
  const initialView: AtendimentoView = rawVisao === 'finalizados' ? 'FINALIZADOS' : 'ATIVOS';
  const parsedAtendimentoId = rawAtendimentoId ? Number(rawAtendimentoId) : null;
  const initialAtendimentoId = parsedAtendimentoId
    && Number.isSafeInteger(parsedAtendimentoId)
    && parsedAtendimentoId > 0
    ? parsedAtendimentoId
    : null;
  const initialPage = await getAtendimentosIniciais();
  const atendentes = user.perfil === 'MEDICO' ? [] : await getAtendentesAtivos();

  return (
    <AtendimentosClient
      initialConversations={initialPage.content}
      atendentes={atendentes}
      user={user}
      initialAtendimentoId={initialAtendimentoId}
      initialView={initialView}
    />
  );
}
