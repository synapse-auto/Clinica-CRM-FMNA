import { redirect } from 'next/navigation';
import { MensagensRapidasClient } from '@/components/operacional/MensagensRapidasClient';
import { requireSession } from '@/lib/auth/session';
import {
  getCategoriasMensagensRapidas,
  getMensagensRapidas,
  isBackendAuthorizationError,
} from '@/services/backend';
import type { CategoriaMensagemRapida, MensagemRapida } from '@/types/operacional';

export default async function MensagensRapidasPage() {
  const user = await requireSession();
  let messages: MensagemRapida[] = [];
  let categories: CategoriaMensagemRapida[] = [];
  let error: string | null = null;

  try {
    [messages, categories] = await Promise.all([
      getMensagensRapidas(),
      getCategoriasMensagensRapidas(),
    ]);
  } catch (caughtError) {
    if (isBackendAuthorizationError(caughtError)) {
      redirect('/login');
    }
    error = 'Não foi possível carregar as mensagens rápidas. Verifique a conexão com o servidor.';
  }

  return (
    <div className="h-full overflow-auto bg-clinic-canvas p-4 custom-scrollbar">
      <MensagensRapidasClient
        initialMessages={messages}
        categories={categories}
        initialError={error}
        canManage={user.perfil === 'GESTOR'}
      />
    </div>
  );
}
